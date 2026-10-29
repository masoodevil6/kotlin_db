package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteAstComposer
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.meta_data.analysis.EffectiveQueryMetadataAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@QBQuery(name = "provider_query", alias = "provider_source")
private class ProviderQueryDeclaration : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "logical_identifier", alias = "published_identifier")
    val id: Long
        get() = error("Metadata extraction must not invoke output getters")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(ProviderQueryDeclaration::id)
            }
            addColumn {
                column { tableAttribute("'label'") }
                alias("published_label")
            }
        }
    }
}

@QBQuery(name = "dsl_alias_query", alias = "dsl_alias_source")
private class DslAliasQueryDeclaration : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "logical_value", alias = "annotation_value")
    val value: Long
        get() = error("Metadata extraction must not invoke output getters")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(DslAliasQueryDeclaration::value)
                alias("dsl_published_value")
                execute(DataType.LONG)
            }
        }
    }
}

@QBQuery(name = "dual_typed_query", alias = "dual_typed_source")
private class DualTypedProviderQueryDeclaration : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "logical_long", alias = "published_long")
    val longValue: Long
        get() = error("Metadata extraction must not invoke output getters")

    @QBQuerySelect(name = "logical_text", alias = "published_text")
    val textValue: String
        get() = error("Metadata extraction must not invoke output getters")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(DualTypedProviderQueryDeclaration::longValue)
                execute(DataType.LONG)
            }
            addColumn {
                column { tableAttribute("'text'") }
                alias(DualTypedProviderQueryDeclaration::textValue)
                execute(DataType.STRING)
            }
        }
    }
}

@QBTable(name = "metadata_projection_model", alias = "mpm")
private class MetadataProjectionModel : IModelBase {
    @QBColumn(name = "age")
    val age: Int
        get() = error("Metadata extraction must not invoke model property getters")

    @QBColumn(name = "nullable_age")
    val nullableAge: Int?
        get() = error("Metadata extraction must not invoke model property getters")

    @QBColumn(name = "nullable_name")
    val nullableName: String?
        get() = error("Metadata extraction must not invoke model property getters")
}

@QBQuery(name = "model_backed_metadata_cte")
private class ModelBackedMetadataCte : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        from { model(MetadataProjectionModel::class) }
        select {
            addColumn {
                column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::age) }
                alias("inner_age")
            }
        }
    }
}

class EffectiveQueryMetadataProviderTest {

    private fun inlineCteSource(relation: Cte, alias: String): QueryRenderSelectBuilder =
        QueryRenderSelectBuilder().also { query ->
            CteAstComposer(query.ast, ModelTableAliasContext(), CteSourceContext())
                .addInlineCteSource(relation, alias)
        }

    @Test
    fun `provider returns no synthetic relation for an ordinary query without a CTE`() {
        val query = QueryRenderSelectBuilder()
            .from { table("users") }
            .select {
                addColumn { column { tableAttribute("1") } }
            }

        val metadata = EffectiveQueryMetadataAnalyzer().analyze(query.ast)

        assertEquals(emptyList(), metadata.relations)
        assertEquals(listOf(null), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(null), metadata.outputs.map { it.executionType })
    }

    @Test
    fun `table metadata records CTE identity only for CTE source paths`() {
        val regularTable = QueryRenderSelectBuilder().from { table("named_relation") }
        val namedCte = QueryRenderSelectBuilder().from { cte("named_relation") }
        val derivedSelect = QueryRenderSelectBuilder().from {
            table {
                select { addColumn { column { tableAttribute("1") }; alias("value") } }
            }.alias("derived")
        }
        val inlineCte = QueryRenderSelectBuilder().from { cte(ProviderQueryDeclaration().createCte(Unit)) }
        val composedRelation = ProviderQueryDeclaration().createCte(Unit)
        val composedCte = QueryRenderSelectBuilder()
            .withs { addWith { with(composedRelation) } }
            .from { cte(composedRelation.name) }

        assertNull(regularTable.ast.table!!.metadata.cteRuntimeName)
        assertNull(regularTable.ast.table!!.metadata.logicalQueryName)
        assertEquals(false, regularTable.ast.table!!.metadata.isCteObjectSource)
        assertEquals(regularTable.ast.table!!.metadata.resolveName(), namedCte.ast.table!!.metadata.resolveName())
        assertEquals("named_relation", namedCte.ast.table!!.metadata.cteRuntimeName)
        assertNull(namedCte.ast.table!!.metadata.logicalQueryName)
        assertEquals(false, namedCte.ast.table!!.metadata.isCteObjectSource)
        assertNull(derivedSelect.ast.table!!.metadata.cteRuntimeName)
        assertNull(derivedSelect.ast.table!!.metadata.logicalQueryName)
        assertEquals(false, derivedSelect.ast.table!!.metadata.isCteObjectSource)
        assertEquals("provider_query", inlineCte.ast.table!!.metadata.cteRuntimeName)
        assertEquals("provider_query", inlineCte.ast.table!!.metadata.logicalQueryName)
        assertEquals(true, inlineCte.ast.table!!.metadata.isCteObjectSource)
        assertEquals("provider_query", composedCte.ast.table!!.metadata.cteRuntimeName)
        assertEquals("provider_query", composedCte.ast.table!!.metadata.logicalQueryName)
        assertEquals(false, composedCte.ast.table!!.metadata.isCteObjectSource)
    }

    @Test
    fun `provider exposes final select outputs for a regular table query`() {
        val query = QueryRenderSelectBuilder()
            .from { table("user_users").alias("uu") }
            .select {
                addColumn { column { tableColumn("uu", "id") }; alias("user_id"); execute(DataType.LONG) }
                addColumn {
                    column { tableAttribute("concat( uu.name , '-' , uu.family )") }
                    alias("user_full_name"); execute(DataType.STRING)
                }
                addColumn { column { tableColumn("uu", "age") }; alias("user_age"); execute(DataType.INT) }
            }

        val metadata = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
        val astOutputs = query.ast.select!!.columns

        assertEquals(emptyList(), metadata.relations)
        assertEquals(listOf(null, null, null), metadata.outputs.map { it.logicalOutputName })
        assertEquals(listOf("user_id", "user_full_name", "user_age"), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG, DataType.STRING, DataType.INT), metadata.outputs.map { it.executionType })
        assertEquals(listOf(null, null, null), astOutputs.map { it.metadata.logicalOutputName })
        assertEquals(astOutputs.map { it.metadata.ColumnAlias }, metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(
            astOutputs.map { it.metadata.ExecutionType },
            metadata.outputs.map { it.executionType },
        )
        assertTrue(astOutputs.all { it.metadata.IsExecutionTypeExplicit })
    }

    @Test
    fun `provider exposes effective model source for property output`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::age) }
                    alias("display_age")
                }
            }

        val output = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertEquals("age", query.ast.select!!.columns.single().metadata.logicalOutputName)
        assertEquals("metadata_projection_model", output.tableName)
        assertEquals("mpm", output.tableAlias)
        assertEquals("age", output.logicalOutputName)
        assertEquals("display_age", output.publishedSqlAlias)
        assertEquals(DataType.INT, output.executionType)
    }

    @Test
    fun `provider reports DSL table alias override for property output`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class).alias("users_override") }
            .select {
                addColumn {
                    column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::age) }
                    alias("display_age")
                }
            }

        val output = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertEquals("metadata_projection_model", output.tableName)
        assertEquals("users_override", output.tableAlias)
    }

    @Test
    fun `raw expression keeps output metadata without inventing a source`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class).alias("users") }
            .select {
                addColumn {
                    column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::age) }
                    alias("property_age")
                }
                addColumn {
                    column { tableAttribute("concat(users.name, '-', users.family)") }
                    alias("display_name")
                    execute(DataType.STRING)
                }
            }

        val outputs = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs

        assertEquals("metadata_projection_model", outputs[0].tableName)
        assertEquals("users", outputs[0].tableAlias)
        assertEquals("age", outputs[0].logicalOutputName)
        assertEquals("property_age", outputs[0].publishedSqlAlias)
        assertEquals(DataType.INT, outputs[0].executionType)

        assertNull(outputs[1].tableName)
        assertNull(outputs[1].tableAlias)
        assertNull(outputs[1].logicalOutputName)
        assertNull(query.ast.select!!.columns[1].metadata.logicalOutputName)
        assertEquals("display_name", outputs[1].publishedSqlAlias)
        assertEquals(DataType.STRING, outputs[1].executionType)
    }

    @Test
    fun `provider reports CTE occurrence as the direct output source`() {
        val query = inlineCteSource(ModelBackedMetadataCte().createCte(Unit), "outer_cte")
            .select {
                addColumn {
                    column { tableColumn("outer_cte", "inner_age") }
                    alias("final_age")
                    execute(DataType.LONG)
                }
            }

        val metadata = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
        val output = metadata.outputs.single()

        assertEquals("model_backed_metadata_cte", output.tableName)
        assertEquals("outer_cte", output.tableAlias)
        assertEquals("final_age", output.publishedSqlAlias)
        assertEquals(DataType.LONG, output.executionType)
        assertEquals(
            "model_backed_metadata_cte",
            metadata.relations.single { it.occurrenceAlias == "outer_cte" }.cteRuntimeName,
        )
        assertNotEquals("metadata_projection_model", output.tableName)
    }

    @Test
    fun `derived query output reports its occurrence alias without inner table name`() {
        val query = QueryRenderSelectBuilder()
            .from {
                table {
                    from { table("inner_table").alias("inner_source") }
                    select {
                        addColumn {
                            column { tableColumn("inner_source", "id") }
                            alias("inner_id")
                            execute(DataType.LONG)
                        }
                    }
                }.alias("nested_source")
            }
            .select {
                addColumn {
                    column { tableColumn("nested_source", "inner_id") }
                    alias("final_id")
                }
            }

        val output = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertNull(output.tableName)
        assertEquals("nested_source", output.tableAlias)
        assertEquals("final_id", output.publishedSqlAlias)
        assertNull(output.executionType)
    }

    @Test
    fun `unqualified raw expression in multi-source query has no fabricated source`() {
        val query = QueryRenderSelectBuilder()
            .from { table("users").alias("u") }
            .joins {
                addJoin {
                    innerJoin()
                    table { table("details").alias("d") }
                }
            }
            .select {
                addColumn {
                    column { tableAttribute("concat(u.name, '-', d.name)") }
                    alias("combined_name")
                }
            }

        val output = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertNull(output.tableName)
        assertNull(output.tableAlias)
        assertEquals("combined_name", output.publishedSqlAlias)
        assertNull(output.executionType)
    }

    @Test
    fun `provider reports only final select outputs for a CTE query`() {
        val query = inlineCteSource(ProviderQueryDeclaration().createCte(Unit), "provider_source")
            .select {
                addColumn {
                    column { tableColumn("provider_source", "published_identifier") }
                    alias("final_identifier")
                    execute(DataType.STRING)
                }
            }

        val metadata = EffectiveQueryMetadataAnalyzer().analyze(query.ast)

        assertEquals(
            listOf("published_identifier", "published_label"),
            metadata.relations.single { it.occurrenceAlias == "provider_source" }
                .outputs.map { it.publishedSqlAlias },
        )
        assertEquals(listOf("final_identifier"), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.STRING), metadata.outputs.map { it.executionType })
        assertFalse(metadata.outputs.any { it.publishedSqlAlias in setOf("published_identifier", "published_label") })
    }

    @Test
    fun `provider reports only outer projection for a nested select source`() {
        val query = QueryRenderSelectBuilder()
            .from {
                table {
                    from { table("inner_table").alias("inner_source") }
                    select {
                        addColumn {
                            column { tableColumn("inner_source", "id") }
                            alias("inner_identifier")
                            execute(DataType.STRING)
                        }
                    }
                }.alias("nested_source")
            }
            .select {
                addColumn {
                    column { tableColumn("nested_source", "inner_identifier") }
                    alias("final_identifier")
                    execute(DataType.LONG)
                }
            }

        val metadata = EffectiveQueryMetadataAnalyzer().analyze(query.ast)

        assertEquals(emptyList(), metadata.relations)
        assertEquals(listOf("final_identifier"), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG), metadata.outputs.map { it.executionType })
        assertEquals("inner_identifier", query.ast.table!!.derivedQuery!!.select!!.columns.single().metadata.ColumnAlias)
        assertFalse(metadata.outputs.any { it.publishedSqlAlias == "inner_identifier" })
    }

    @Test
    fun `provider exposes inferred execution type without requiring explicit execute`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::age) }
                    alias("display_age")
                }
            }

        val astOutput = query.ast.select!!.columns.single()
        val metadataOutput = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertEquals(DataType.INT, astOutput.metadata.ExecutionType)
        assertFalse(astOutput.metadata.IsExecutionTypeExplicit)
        assertEquals("age", metadataOutput.logicalOutputName)
        assertEquals("display_age", metadataOutput.publishedSqlAlias)
        assertEquals(DataType.INT, metadataOutput.executionType)
    }

    @Test
    fun `provider maps nullable property types to their non-null execution type`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::nullableAge) }
                    alias("display_nullable_age")
                }
            }

        val astOutput = query.ast.select!!.columns.single()
        val metadataOutput = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertTrue(MetadataProjectionModel::nullableAge.returnType.isMarkedNullable)
        assertEquals(DataType.INT, astOutput.metadata.ExecutionType)
        assertFalse(astOutput.metadata.IsExecutionTypeExplicit)
        assertEquals(DataType.INT, metadataOutput.executionType)
    }

    @Test
    fun `explicit execution type overrides inferred property type in effective metadata`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::age) }
                    alias("display_age_as_long")
                    execute(DataType.LONG)
                }
            }

        val astOutput = query.ast.select!!.columns.single()
        val metadataOutput = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertEquals(Int::class, MetadataProjectionModel::age.returnType.classifier)
        assertEquals(DataType.LONG, astOutput.metadata.ExecutionType)
        assertTrue(astOutput.metadata.IsExecutionTypeExplicit)
        assertEquals(DataType.LONG, metadataOutput.executionType)
    }

    @Test
    fun `explicit execution type does not change nullable string property metadata`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column { tableSelector(MetadataProjectionModel::class); columnSelector(MetadataProjectionModel::class, MetadataProjectionModel::nullableName) }
                    alias("nullable_name_as_long")
                    execute(DataType.LONG)
                }
            }

        val propertyType = MetadataProjectionModel::nullableName.returnType
        val metadataOutput = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .outputs
            .single()

        assertEquals(String::class, propertyType.classifier)
        assertTrue(propertyType.isMarkedNullable)
        assertEquals(DataType.LONG, metadataOutput.executionType)
    }

    @Test
    fun `provider exposes inline declaration metadata in AST output order`() {
        val query = inlineCteSource(ProviderQueryDeclaration().createCte(Unit), "explicit_occurrence")

        val metadata = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
        val relation = metadata.relations.single { it.occurrenceAlias == "explicit_occurrence" }

        assertEquals("provider_query", query.ast.table!!.metadata.cteRuntimeName)
        assertEquals("provider_query", query.ast.table!!.metadata.logicalQueryName)
        assertEquals("logical_identifier", query.ast.table!!.derivedQuery!!.select!!.columns[0].metadata.logicalOutputName)

        assertEquals("provider_query", relation.logicalQueryName)
        assertEquals("provider_query", relation.cteRuntimeName)
        assertEquals(null, relation.withSqlName)
        assertEquals("explicit_occurrence", relation.occurrenceAlias)
        assertEquals(
            listOf("published_identifier", "published_label"),
            relation.outputs.map { it.publishedSqlAlias },
        )
        assertEquals(
            listOf("logical_identifier", null),
            relation.outputs.map { it.logicalOutputName },
        )
        assertEquals(listOf(DataType.LONG, null), relation.outputs.map { it.executionType })
    }

    @Test
    fun `provider exposes explicit WITH name separately from CTE runtime name`() {
        val relation = ProviderQueryDeclaration().createCte(Unit)
        val query = QueryRenderSelectBuilder()
            .withs { addWith { with("published_with_name", relation) } }

        val metadata = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
        val relationMetadata = metadata.relations.single()

        assertEquals("provider_query", query.ast.withs!!.withs.single().metadata.cteRuntimeName)
        assertEquals("provider_query", query.ast.withs!!.withs.single().metadata.logicalQueryName)

        assertEquals("provider_query", relationMetadata.cteRuntimeName)
        assertEquals("published_with_name", relationMetadata.withSqlName)
        assertEquals(null, relationMetadata.occurrenceAlias)
    }

    @Test
    fun `provider keeps WITH entry and source occurrence as separate metadata entries`() {
        val relation = ProviderQueryDeclaration().createCte(Unit)
        val query = QueryRenderSelectBuilder()
            .withs { addWith { with("published_with_name", relation) } }
            .from { cte("published_with_name").alias("source_occurrence") }

        val relations = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .relations

        assertEquals(2, relations.size)
        assertEquals("published_with_name", relations[0].withSqlName)
        assertEquals(null, relations[0].occurrenceAlias)
        assertEquals("published_with_name", relations[1].withSqlName)
        assertEquals("source_occurrence", relations[1].occurrenceAlias)
    }

    @Test
    fun `provider preserves logical output when DSL overrides the published SQL alias`() {
        val query = inlineCteSource(DslAliasQueryDeclaration().createCte(Unit), "dsl_occurrence")

        val output = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .relations
            .single { it.occurrenceAlias == "dsl_occurrence" }
            .outputs
            .single()

        assertEquals("logical_value", output.logicalOutputName)
        assertEquals("dsl_published_value", output.publishedSqlAlias)
        assertEquals(DataType.LONG, output.executionType)
    }

    @Test
    fun `provider metadata matches final SQL alias and AST execution type`() {
        val query = inlineCteSource(ProviderQueryDeclaration().createCte(Unit), "final_source")

        val relation = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .relations
            .single { it.occurrenceAlias == "final_source" }
        val astOutput = query.ast.table!!.derivedQuery!!.select!!.columns.first()
        val sql = MySqlDialect().render(query.ast)!!

        assertEquals(astOutput.metadata.ColumnAlias, relation.outputs.first().publishedSqlAlias)
        assertEquals(astOutput.metadata.ExecutionType, relation.outputs.first().executionType)
        assertTrue(sql.contains("published_identifier", ignoreCase = true), sql)
    }

    @Test
    fun `provider preserves order and identity for multiple typed outputs`() {
        val query = inlineCteSource(DualTypedProviderQueryDeclaration().createCte(Unit), "dual_source")

        val relation = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .relations
            .single { it.occurrenceAlias == "dual_source" }
        val astOutputs = query.ast.table!!.derivedQuery!!.select!!.columns
        val outputs = relation.outputs

        assertEquals(listOf("logical_long", "logical_text"), outputs.map { it.logicalOutputName })
        assertEquals(listOf("published_long", "published_text"), outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG, DataType.STRING), outputs.map { it.executionType })
        assertEquals(astOutputs.map { it.metadata.ExecutionType }, outputs.map { it.executionType })
        assertEquals(astOutputs.map { it.metadata.ColumnAlias }, outputs.map { it.publishedSqlAlias })
        assertTrue(outputs.all { it.logicalOutputName != it.publishedSqlAlias })
        assertNotEquals(outputs[0].logicalOutputName, outputs[1].logicalOutputName)
    }

    @Test
    fun `provider reports dependency CTE metadata in dependency first order`() {
        val base = cte("metadata_base") {
            select { addColumn { column { tableAttribute("1") }; alias("base_value") } }
        }
        val dependent = cte("metadata_dependent") {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select { addColumn { column { cteColumn(base, "base_value") }; alias("dependent_value") } }
        }

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(dependent) } }

        val relations = EffectiveQueryMetadataAnalyzer().analyze(query.ast)
            .relations

        assertEquals(
            listOf("metadata_base", "metadata_dependent"),
            query.ast.withs!!.withs.map { it.metadata.cteRuntimeName },
        )
        assertEquals(listOf(null, null), query.ast.withs!!.withs.map { it.metadata.logicalQueryName })

        assertEquals(listOf("metadata_base", "metadata_dependent"), relations.map { it.cteRuntimeName })
        assertEquals(listOf("metadata_base", "metadata_dependent"), relations.map { it.withSqlName })
        assertEquals(listOf("base_value", "dependent_value"), relations.map { it.outputs.single().publishedSqlAlias })
    }
}
