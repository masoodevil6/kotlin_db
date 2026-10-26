package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.meta_data.analysis.EffectiveQueryMetadataAnalyzer
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@QBQuery(name = "metadata_with_name", alias = "metadata_with_source")
private class MetadataNamedWithDeclaration : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

class CteWithsIntegrationTest {

    @Test
    fun `with Cte uses its resolved declaration name and keeps its default source alias separate`() {
        val cte = MetadataNamedWithDeclaration().createCte(Unit)
        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(cte) } }
            .from { cte("metadata_with_name") }
            .select { addColumn { column { cteColumn(cte, "value") } } }

        val sql = MySqlDialect().render(query.ast)!!.replace(Regex("\\s+"), " ").lowercase()

        assertEquals("metadata_with_name", query.ast.withs!!.withs.single().metadata.withName)
        assertEquals("metadata_with_name", query.ast.table!!.metadata.nameFromAst)
        assertEquals("metadata_with_source", query.ast.table!!.metadata.aliasFromAst)
        assertTrue(sql.contains("metadata_with_name as"), sql)
        assertTrue(sql.contains("from metadata_with_name as metadata_with_source"), sql)
    }

    @Test
    fun `explicit WITH name overrides Cte runtime name without changing occurrence alias`() {
        val cte = cte("factory_runtime_name", MetadataNamedWithDeclaration::class) {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val defaultNamedQuery = QueryRenderSelectBuilder().withs {
            addWith { with(cte) }
        }
        assertEquals("factory_runtime_name", defaultNamedQuery.ast.withs!!.withs.single().metadata.withName)

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with("explicit_with_name", cte) } }
            .from { cte("explicit_with_name").alias("explicit_occurrence_alias") }
            .select { addColumn { column { cteColumn(cte, "value") } } }

        val sql = MySqlDialect().render(query.ast)!!.replace(Regex("\\s+"), " ").lowercase()

        assertEquals("explicit_with_name", query.ast.withs!!.withs.single().metadata.withName)
        assertEquals("explicit_with_name", query.ast.table!!.metadata.nameFromAst)
        assertEquals("explicit_occurrence_alias", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("explicit_occurrence_alias", query.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())
        assertEquals("factory_runtime_name", cte.name)
        assertTrue(sql.contains("explicit_with_name as"), sql)
        assertTrue(sql.contains("from explicit_with_name as explicit_occurrence_alias"), sql)
        assertTrue(!sql.contains("factory_runtime_name as"), sql)
    }

    @Test
    fun `explicit WITH name is used by dependent Cte source references`() {
        val base = cte("base_runtime_name") {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val dependent = cte("dependent_runtime_name") {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select { addColumn { column { cteColumn(base, "value") }; alias("value") } }
        }

        val query = QueryRenderSelectBuilder().withs {
            addWith { with("renamed_base", base) }
            addWith { with(dependent) }
        }
        val withItems = query.ast.withs!!.withs
        val dependentBodyTable = withItems[1].withBody!!.table!!

        assertEquals(listOf("renamed_base", "dependent_runtime_name"), withItems.map { it.metadata.withName })
        assertEquals("renamed_base", dependentBodyTable.metadata.nameFromAst)
        assertEquals("base_runtime_name", dependentBodyTable.metadata.aliasFromAst)
        assertEquals("base_runtime_name", dependent.definition.ast.table!!.metadata.resolveName())
    }

    @Test
    fun `composed WITH AST retains runtime logical and effective names independently`() {
        val base = MetadataNamedWithDeclaration().createCte(Unit)
        val dependent = cte("dependent_runtime_name") {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }

        val query = QueryRenderSelectBuilder()
            .withs {
                addWith { with("base_effective_name", base) }
                addWith { with(dependent) }
            }
            .from { cte(dependent.name) }

        val items = query.ast.withs!!.withs
        assertEquals(listOf("base_effective_name", "dependent_runtime_name"), items.map { it.metadata.withName })
        assertEquals(listOf(base.name, dependent.name), items.map { it.metadata.cteRuntimeName })
        assertEquals(listOf("metadata_with_name", null), items.map { it.metadata.logicalQueryName })
        assertEquals("dependent_runtime_name", query.ast.table!!.metadata.cteRuntimeName)
        assertEquals(null, query.ast.table!!.metadata.logicalQueryName)

        val dependencyTable = items[1].withBody!!.table!!
        assertEquals("base_effective_name", dependencyTable.metadata.resolveName())
        assertEquals(base.name, dependencyTable.metadata.cteRuntimeName)
        assertEquals("metadata_with_name", dependencyTable.metadata.logicalQueryName)
    }

    @Test
    fun `raw WITH item and ordinary derived SELECT do not acquire CTE identity`() {
        val query = QueryRenderSelectBuilder()
            .withs {
                addWith {
                    with("raw_relation") {
                        select { addColumn { column { tableAttribute("1") }; alias("value") } }
                    }
                }
            }
            .from {
                table {
                    select { addColumn { column { tableAttribute("1") }; alias("value") } }
                }.alias("derived_source")
            }

        assertEquals("raw_relation", query.ast.withs!!.withs.single().metadata.withName)
        assertEquals(null, query.ast.withs!!.withs.single().metadata.cteRuntimeName)
        assertEquals(null, query.ast.withs!!.withs.single().metadata.logicalQueryName)
        assertEquals(null, query.ast.table!!.metadata.cteRuntimeName)
        assertEquals(null, query.ast.table!!.metadata.logicalQueryName)
    }

    @Test
    fun `prebuilt query AST retains CTE identity and effective WITH name`() {
        val cte = MetadataNamedWithDeclaration().createCte(Unit)
        val original = QueryRenderSelectBuilder()
            .withs { addWith { with("stored_with_name", cte) } }
            .from { cte("stored_with_name").alias("stored_occurrence") }

        val rebuilt = QueryRenderSelectBuilder(original.ast)
        val item = rebuilt.ast.withs!!.withs.single()
        val source = rebuilt.ast.table!!

        assertEquals("stored_with_name", item.metadata.withName)
        assertEquals(cte.name, item.metadata.cteRuntimeName)
        assertEquals("metadata_with_name", item.metadata.logicalQueryName)
        assertEquals(cte.name, source.metadata.cteRuntimeName)
        assertEquals("metadata_with_name", source.metadata.logicalQueryName)
        assertEquals("stored_occurrence", source.metadata.resolveAlias())
    }

    @Test
    fun `explicit WITH name rejects blanks while duplicate SQL names pass through`() {
        val cte = cte("original_name") {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val cteWithDuplicateName = cte("same_name") {
            select { addColumn { column { tableAttribute("2") }; alias("value") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().withs { addWith { with("", cte) } }
        }

        val duplicateNames = QueryRenderSelectBuilder().withs {
            addWith { with("same_name", cte) }
            addWith { with(cteWithDuplicateName) }
        }
        assertEquals(listOf("same_name", "same_name"), duplicateNames.ast.withs!!.withs.map { it.metadata.withName })
        assertTrue(MySqlDialect().render(duplicateNames.ast)!!.contains("same_name AS"))
    }

    @Test
    fun `with accepts a Cte and composes dependencies and parameters in dependency-first order`() {
        val base = cte("with_base") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("base_id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("base_value")
                            paramValue(11)
                        }
                    }
                }
            }
        }
        val dependent = cte("with_dependent") {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select {
                addColumn {
                    column { cteColumn(base, "base_id") }
                    alias("dependent_id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("with_base", "base_id") }
                        operationEqual()
                        sideValueParam {
                            paramName("dependent_value")
                            paramValue(22)
                        }
                    }
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(dependent) } }
            .select { addColumn { column { tableAttribute("1") }; alias("result") } }
            .from { table("outer_table") }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValueParam {
                            paramName("outer_value")
                            paramValue(33)
                        }
                    }
                }
            }

        val names = query.ast.withs!!.withs.mapNotNull { it.metadata.withName }
        val sql = MySqlDialect().render(query.ast)!!

        assertEquals(listOf("with_base", "with_dependent"), names)
        val parameterOccurrences = renderedParameterResult(query.ast).parameterOccurrences
        assertEquals(listOf("base_value", "dependent_value", "outer_value"), parameterOccurrences.map { it.name })
        assertEquals(listOf(11, 22, 33), parameterOccurrences.map { it.value })
        assertTrue(sql.indexOf("with_base AS") < sql.indexOf("with_dependent AS"), sql)
        assertTrue(sql.indexOf(":base_value") < sql.indexOf(":dependent_value"), sql)
        assertTrue(sql.indexOf(":dependent_value") < sql.indexOf(":outer_value"), sql)
    }

    @Test
    fun `repeated Cte roots and the same source are emitted once by composition identity`() {
        val cte = cte("single_with_root") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("id")
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .withs {
                addWith { with(cte) }
                addWith { with(cte) }
            }
            .from { cte("single_with_root").alias("source") }
            .select { addColumn { column { cteColumn(cte, "id") } } }

        assertEquals(listOf("single_with_root"), query.ast.withs!!.withs.mapNotNull { it.metadata.withName })
        assertEquals(listOf("single_with_root"), query.ast.withs!!.withs.mapNotNull { it.metadata.cteRuntimeName })
        assertEquals(null, query.ast.withs!!.withs.single().metadata.logicalQueryName)
        assertEquals("source", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("single_with_root", query.ast.table!!.metadata.cteRuntimeName)
        assertEquals("source", query.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())
    }

    @Test
    fun `AST WITH entries preserve explicit order while composing dependency closure`() {
        val base = cte("closure_base") {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val first = cte("closure_first") {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select { addColumn { column { cteColumn(base, "value") }; alias("first_value") } }
        }
        val second = cte("closure_second") {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select { addColumn { column { cteColumn(base, "value") }; alias("second_value") } }
        }

        val query = QueryRenderSelectBuilder()
            .withs {
                addWith {
                    with("raw_head") {
                        select { addColumn { column { tableAttribute("0") }; alias("raw_value") } }
                    }
                }
                addWith { with(first) }
                addWith { with(first) }
                addWith { with(second) }
                addWith {
                    with("raw_tail") {
                        select { addColumn { column { tableAttribute("2") }; alias("raw_value") } }
                    }
                }
            }
            .from { cte("closure_second") }

        val withItems = query.ast.withs!!.withs
        val expectedKnownRelations = listOf("closure_base", "closure_first", "closure_second")
        assertEquals(listOf("raw_head") + expectedKnownRelations + "raw_tail", withItems.map { it.metadata.withName })
        assertEquals(listOf(null) + expectedKnownRelations + null, withItems.map { it.metadata.cteRuntimeName })
        assertEquals(listOf(null, null, null, null, null), withItems.map { it.metadata.logicalQueryName })
        assertEquals(null, withItems.last().metadata.cteRuntimeName)

        val reported = EffectiveQueryMetadataAnalyzer().analyze(query.ast).relations
        assertEquals(
            listOf("raw_head") + expectedKnownRelations + listOf("raw_tail", "closure_second"),
            reported.mapNotNull { it.cteRuntimeName ?: it.withSqlName },
        )
        assertEquals(expectedKnownRelations.size + 3, reported.size)
        assertEquals("closure_second", reported.last().occurrenceAlias)
    }

    @Test
    fun `duplicate names between Cte and raw WITH items are rendered for SQL validation`() {
        val cte = cte("duplicate_with_name") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("id")
                }
            }
        }

        val query = QueryRenderSelectBuilder().withs {
            addWith { with(cte) }
            addWith {
                with("duplicate_with_name") {
                    select { addColumn { column { tableColumn("u", "id") } } }
                }
            }
        }

        assertEquals(listOf("duplicate_with_name", "duplicate_with_name"), query.ast.withs!!.withs.map { it.metadata.withName })
        assertTrue(MySqlDialect().render(query.ast)!!.contains("duplicate_with_name AS"))
    }

    @Test
    fun `cyclic Cte dependency metadata is passed through to SQL rendering`() {
        val seed = cte("with_cycle") {
            from { cte("with_cycle") }
            select {
                addColumn {
                    column { tableAttribute("id") }
                    alias("id")
                }
            }
        }
        val cyclicDependencies = mutableListOf<Cte>()
        val cyclic = object : Cte by seed {
            override val name: String = "with_cycle"
            override val dependencies: List<Cte>
                get() = cyclicDependencies
        }
        cyclicDependencies += cyclic

        val query = QueryRenderSelectBuilder().withs { addWith { with(cyclic) } }

        assertEquals(listOf("with_cycle"), query.ast.withs!!.withs.map { it.metadata.withName })
        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("with_cycle", ignoreCase = true), sql)
    }

    @Test
    fun `undefined named CTE source is rendered without Builder semantic validation`() {
        val query = QueryRenderSelectBuilder()
            .from { cte("not_declared") }
            .select { addColumn { column { tableAttribute("value") }; alias("value") } }

        assertEquals("not_declared", query.ast.table!!.metadata.nameFromAst)
        assertEquals("not_declared", query.ast.table!!.metadata.cteRuntimeName)
        assertEquals(null, query.ast.table!!.metadata.logicalQueryName)
        assertEquals(false, query.ast.table!!.metadata.isCteObjectSource)
        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("not_declared", ignoreCase = true), sql)

        val declarationWithRawNamedSource = cte("raw_named_source_declaration") {
            from { cte("not_declared") }
            select { addColumn { column { tableAttribute("value") }; alias("value") } }
        }
        assertEquals(0, declarationWithRawNamedSource.dependencies.size)
    }

    @Test
    fun `named WITH source binds in either call order and retains relation metadata and occurrence alias`() {
        val relation = MetadataNamedWithDeclaration().createCte(Unit)
        val sqlName = "metadata_with_override"

        val fromBeforeWiths = QueryRenderSelectBuilder()
            .from { cte(sqlName).alias("explicit_source_alias") }
            .withs { addWith { with(sqlName, relation) } }
            .select { addColumn { column { cteColumn(relation, "value") } } }

        val withsBeforeFrom = QueryRenderSelectBuilder()
            .withs { addWith { with(sqlName, relation) } }
            .from { cte(sqlName) }
            .select { addColumn { column { cteColumn(relation, "value") } } }

        val explicitSource = fromBeforeWiths.ast.table!!
        assertEquals(sqlName, explicitSource.metadata.nameFromAst)
        assertEquals(relation.name, explicitSource.metadata.cteRuntimeName)
        assertEquals("metadata_with_name", explicitSource.metadata.logicalQueryName)
        assertEquals("explicit_source_alias", explicitSource.metadata.aliasFromAst)
        assertEquals(false, explicitSource.metadata.isCteObjectSource)
        assertEquals(
            "explicit_source_alias",
            fromBeforeWiths.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias(),
        )
        assertEquals(
            "value",
            fromBeforeWiths.ast.select!!.columns.single().Column!!.metadata.columnNameFromAst,
        )
        assertEquals(sqlName, fromBeforeWiths.ast.withs!!.withs.single().metadata.withName)
        val fromBeforeWithsSql = MySqlDialect().render(fromBeforeWiths.ast)!!
            .replace(Regex("\\s+"), " ")
        assertTrue(
            fromBeforeWithsSql.contains("from $sqlName as explicit_source_alias", ignoreCase = true),
        )
        val analyzedOccurrence = EffectiveQueryMetadataAnalyzer().analyze(fromBeforeWiths.ast).relations.last()
        assertEquals("metadata_with_name", analyzedOccurrence.logicalQueryName)
        assertEquals(relation.name, analyzedOccurrence.cteRuntimeName)
        assertEquals(sqlName, analyzedOccurrence.withSqlName)
        assertEquals("explicit_source_alias", analyzedOccurrence.occurrenceAlias)

        val defaultAliasedSource = withsBeforeFrom.ast.table!!
        assertEquals(sqlName, defaultAliasedSource.metadata.nameFromAst)
        assertEquals(relation.name, defaultAliasedSource.metadata.cteRuntimeName)
        assertEquals("metadata_with_name", defaultAliasedSource.metadata.logicalQueryName)
        assertEquals("metadata_with_source", defaultAliasedSource.metadata.aliasFromAst)
        assertEquals(
            "metadata_with_source",
            withsBeforeFrom.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias(),
        )
    }

    @Test
    fun `ambiguous named WITH source stays raw and rejects typed references explicitly`() {
        val first = cte("ambiguous_first") {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val second = cte("ambiguous_second") {
            select { addColumn { column { tableAttribute("2") }; alias("value") } }
        }
        val ambiguousSqlName = "same_effective_name"
        val query = QueryRenderSelectBuilder()
            .withs {
                addWith { with(ambiguousSqlName, first) }
                addWith { with(ambiguousSqlName, second) }
            }
            .from { cte(ambiguousSqlName) }

        assertEquals(ambiguousSqlName, query.ast.table!!.metadata.nameFromAst)
        assertEquals(ambiguousSqlName, query.ast.table!!.metadata.cteRuntimeName)
        assertEquals(false, query.ast.table!!.metadata.isCteObjectSource)
        val failure = assertFailsWith<IllegalArgumentException> {
            query.select { addColumn { column { cteColumn(first, "value") } } }
        }
        assertEquals(
            "Named CTE source '$ambiguousSqlName' is ambiguous in WITH; typed references require a unique relation",
            failure.message,
        )
    }

    @Test
    fun `named WITH dependencies form a unique dependency-first closure and keep parameter order`() {
        val base = cte("named_dependency_base") {
            from { table("users").alias("u") }
            select {
                addColumn { column { tableColumn("u", "id") }; alias("base_id") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("base_value")
                            paramValue(11)
                        }
                    }
                }
            }
        }
        val dependent = cte("named_dependency_child") {
            withs { addWith { with("inner_base_name", base) } }
            from { cte("inner_base_name") }
            select {
                addColumn { column { cteColumn(base, "base_id") }; alias("dependent_id") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { cteColumn(base, "base_id") }
                        operationEqual()
                        sideValueParam {
                            paramName("dependent_value")
                            paramValue(22)
                        }
                    }
                }
            }
        }

        assertEquals(listOf(base), dependent.dependencies)

        val consumer = cte("named_dependency_consumer") {
            withs {
                addWith { with("outer_child_name", dependent) }
                addWith { with("outer_base_name", base) }
                addWith { with("outer_child_name", dependent) }
            }
            from { cte("outer_child_name").alias("child_source") }
            select {
                addColumn { column { cteColumn(dependent, "dependent_id") }; alias("result") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { cteColumn(dependent, "dependent_id") }
                        operationEqual()
                        sideValueParam {
                            paramName("consumer_value")
                            paramValue(33)
                        }
                    }
                }
            }
        }

        assertEquals(listOf(base, dependent), consumer.dependencies)
        assertEquals(
            listOf("outer_base_name", "outer_child_name"),
            consumer.definition.ast.withs!!.withs.map { it.metadata.withName },
        )
        val childBody = consumer.definition.ast.withs!!.withs[1].withBody!!
        assertEquals(0, childBody.withs?.withs?.size ?: 0)
        assertEquals("outer_base_name", childBody.table!!.metadata.nameFromAst)
        assertEquals(base.name, childBody.table!!.metadata.cteRuntimeName)
        assertEquals(
            listOf("base_value", "dependent_value", "consumer_value"),
            renderedParameterResult(consumer.definition.ast).parameterOccurrences.map { it.name },
        )
        assertEquals(
            listOf(11, 22, 33),
            renderedParameterResult(consumer.definition.ast).parameterOccurrences.map { it.value },
        )
    }
}
