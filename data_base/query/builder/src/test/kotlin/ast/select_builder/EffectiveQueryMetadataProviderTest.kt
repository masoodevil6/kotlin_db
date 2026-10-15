package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadataProvider
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.fromCteInternal
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@QBQuery(name = "provider_query", alias = "provider_source")
private class ProviderQueryDeclaration : CteDeclaration<Unit>(Unit) {
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
private class DslAliasQueryDeclaration : CteDeclaration<Unit>(Unit) {
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
private class DualTypedProviderQueryDeclaration : CteDeclaration<Unit>(Unit) {
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
private class ModelBackedMetadataCte : CteDeclaration<Unit>(Unit) {
    override fun createCte(params: Unit): Cte = buildCte {
        from { model(MetadataProjectionModel::class) }
        select {
            addColumn {
                column(MetadataProjectionModel::class, MetadataProjectionModel::age)
                alias("inner_age")
            }
        }
    }
}

class EffectiveQueryMetadataProviderTest {

    @Test
    fun `provider returns no synthetic relation for an ordinary query without a CTE`() {
        val query = QueryRenderSelectBuilder()
            .from { table("users") }
            .select {
                addColumn { column { tableAttribute("1") } }
            }

        val metadata = assertIs<EffectiveQueryMetadataProvider>(query).effectiveQueryMetadata()

        assertEquals(emptyList(), metadata.relations)
        assertEquals(listOf(null), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(null), metadata.outputs.map { it.executionType })
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

        val metadata = assertIs<EffectiveQueryMetadataProvider>(query).effectiveQueryMetadata()
        val astOutputs = query.ast.select!!.columns

        assertEquals(emptyList(), metadata.relations)
        assertEquals(listOf(null, null, null), metadata.outputs.map { it.logicalOutputName })
        assertEquals(listOf("user_id", "user_full_name", "user_age"), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG, DataType.STRING, DataType.INT), metadata.outputs.map { it.executionType })
        assertEquals(astOutputs.map { it.ColumnAlias }, metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(
            astOutputs.map { it.ExecutionType },
            metadata.outputs.map { it.executionType },
        )
        assertTrue(astOutputs.all { it.IsExecutionTypeExplicit })
    }

    @Test
    fun `provider exposes effective model source for property output`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column(MetadataProjectionModel::class, MetadataProjectionModel::age)
                    alias("display_age")
                }
            }

        val output = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .outputs
            .single()

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
                    column(MetadataProjectionModel::class, MetadataProjectionModel::age)
                    alias("display_age")
                }
            }

        val output = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
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
                    column(MetadataProjectionModel::class, MetadataProjectionModel::age)
                    alias("property_age")
                }
                addColumn {
                    column { tableAttribute("concat(users.name, '-', users.family)") }
                    alias("display_name")
                    execute(DataType.STRING)
                }
            }

        val outputs = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .outputs

        assertEquals("metadata_projection_model", outputs[0].tableName)
        assertEquals("users", outputs[0].tableAlias)
        assertEquals("age", outputs[0].logicalOutputName)
        assertEquals("property_age", outputs[0].publishedSqlAlias)
        assertEquals(DataType.INT, outputs[0].executionType)

        assertNull(outputs[1].tableName)
        assertNull(outputs[1].tableAlias)
        assertNull(outputs[1].logicalOutputName)
        assertEquals("display_name", outputs[1].publishedSqlAlias)
        assertEquals(DataType.STRING, outputs[1].executionType)
    }

    @Test
    fun `provider reports CTE occurrence as the direct output source`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(ModelBackedMetadataCte()).alias("outer_cte") }
            .select {
                addColumn {
                    column { tableColumn("outer_cte", "inner_age") }
                    alias("final_age")
                    execute(DataType.LONG)
                }
            }

        val metadata = assertIs<EffectiveQueryMetadataProvider>(query).effectiveQueryMetadata()
        val output = metadata.outputs.single()

        assertEquals("model_backed_metadata_cte", output.tableName)
        assertEquals("outer_cte", output.tableAlias)
        assertEquals("final_age", output.publishedSqlAlias)
        assertEquals(DataType.LONG, output.executionType)
        assertEquals("model_backed_metadata_cte", metadata.relations.single().cteRuntimeName)
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

        val output = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
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

        val output = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .outputs
            .single()

        assertNull(output.tableName)
        assertNull(output.tableAlias)
        assertEquals("combined_name", output.publishedSqlAlias)
        assertNull(output.executionType)
    }

    @Test
    fun `provider reports only final select outputs for a CTE query`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(ProviderQueryDeclaration()).alias("provider_source") }
            .select {
                addColumn {
                    column { tableColumn("provider_source", "published_identifier") }
                    alias("final_identifier")
                    execute(DataType.STRING)
                }
            }

        val metadata = assertIs<EffectiveQueryMetadataProvider>(query).effectiveQueryMetadata()

        assertEquals(listOf("published_identifier", "published_label"), metadata.relations.single().outputs.map { it.publishedSqlAlias })
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

        val metadata = assertIs<EffectiveQueryMetadataProvider>(query).effectiveQueryMetadata()

        assertEquals(emptyList(), metadata.relations)
        assertEquals(listOf("final_identifier"), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG), metadata.outputs.map { it.executionType })
        assertEquals("inner_identifier", query.ast.table!!.derivedQuery!!.select!!.columns.single().ColumnAlias)
        assertFalse(metadata.outputs.any { it.publishedSqlAlias == "inner_identifier" })
    }

    @Test
    fun `provider exposes inferred execution type without requiring explicit execute`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column(MetadataProjectionModel::class, MetadataProjectionModel::age)
                    alias("display_age")
                }
            }

        val astOutput = query.ast.select!!.columns.single()
        val metadataOutput = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .outputs
            .single()

        assertEquals(DataType.INT, astOutput.ExecutionType)
        assertFalse(astOutput.IsExecutionTypeExplicit)
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
                    column(MetadataProjectionModel::class, MetadataProjectionModel::nullableAge)
                    alias("display_nullable_age")
                }
            }

        val astOutput = query.ast.select!!.columns.single()
        val metadataOutput = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .outputs
            .single()

        assertTrue(MetadataProjectionModel::nullableAge.returnType.isMarkedNullable)
        assertEquals(DataType.INT, astOutput.ExecutionType)
        assertFalse(astOutput.IsExecutionTypeExplicit)
        assertEquals(DataType.INT, metadataOutput.executionType)
    }

    @Test
    fun `explicit execution type overrides inferred property type in effective metadata`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column(MetadataProjectionModel::class, MetadataProjectionModel::age)
                    alias("display_age_as_long")
                    execute(DataType.LONG)
                }
            }

        val astOutput = query.ast.select!!.columns.single()
        val metadataOutput = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .outputs
            .single()

        assertEquals(Int::class, MetadataProjectionModel::age.returnType.classifier)
        assertEquals(DataType.LONG, astOutput.ExecutionType)
        assertTrue(astOutput.IsExecutionTypeExplicit)
        assertEquals(DataType.LONG, metadataOutput.executionType)
    }

    @Test
    fun `explicit execution type does not change nullable string property metadata`() {
        val query = QueryRenderSelectBuilder()
            .from { model(MetadataProjectionModel::class) }
            .select {
                addColumn {
                    column(MetadataProjectionModel::class, MetadataProjectionModel::nullableName)
                    alias("nullable_name_as_long")
                    execute(DataType.LONG)
                }
            }

        val propertyType = MetadataProjectionModel::nullableName.returnType
        val metadataOutput = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .outputs
            .single()

        assertEquals(String::class, propertyType.classifier)
        assertTrue(propertyType.isMarkedNullable)
        assertEquals(DataType.LONG, metadataOutput.executionType)
    }

    @Test
    fun `provider exposes inline declaration metadata in AST output order`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(ProviderQueryDeclaration()).alias("explicit_occurrence") }

        val metadata = assertIs<EffectiveQueryMetadataProvider>(query).effectiveQueryMetadata()
        val relation = metadata.relations.single()

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

        val metadata = assertIs<EffectiveQueryMetadataProvider>(query).effectiveQueryMetadata()
        val relationMetadata = metadata.relations.single()

        assertEquals("provider_query", relationMetadata.cteRuntimeName)
        assertEquals("published_with_name", relationMetadata.withSqlName)
        assertEquals(null, relationMetadata.occurrenceAlias)
    }

    @Test
    fun `provider keeps WITH entry and source occurrence as separate metadata entries`() {
        val relation = ProviderQueryDeclaration().createCte(Unit)
        val query = QueryRenderSelectBuilder()
            .withs { addWith { with("published_with_name", relation) } }
            .fromCteInternal(relation, "source_occurrence")

        val relations = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .relations

        assertEquals(2, relations.size)
        assertEquals("published_with_name", relations[0].withSqlName)
        assertEquals(null, relations[0].occurrenceAlias)
        assertEquals(null, relations[1].withSqlName)
        assertEquals("source_occurrence", relations[1].occurrenceAlias)
    }

    @Test
    fun `provider preserves logical output when DSL overrides the published SQL alias`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(DslAliasQueryDeclaration()).alias("dsl_occurrence") }

        val output = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .relations
            .single()
            .outputs
            .single()

        assertEquals("logical_value", output.logicalOutputName)
        assertEquals("dsl_published_value", output.publishedSqlAlias)
        assertEquals(DataType.LONG, output.executionType)
    }

    @Test
    fun `provider metadata matches final SQL alias and AST execution type`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(ProviderQueryDeclaration()).alias("final_source") }

        val relation = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .relations
            .single()
        val astOutput = query.ast.table!!.derivedQuery!!.select!!.columns.first()
        val sql = MySqlDialect().render(query.ast)!!

        assertEquals(astOutput.ColumnAlias, relation.outputs.first().publishedSqlAlias)
        assertEquals(astOutput.ExecutionType, relation.outputs.first().executionType)
        assertTrue(sql.contains("published_identifier", ignoreCase = true), sql)
    }

    @Test
    fun `provider preserves order and identity for multiple typed outputs`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(DualTypedProviderQueryDeclaration()).alias("dual_source") }

        val relation = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .relations
            .single()
        val astOutputs = query.ast.table!!.derivedQuery!!.select!!.columns
        val outputs = relation.outputs

        assertEquals(listOf("logical_long", "logical_text"), outputs.map { it.logicalOutputName })
        assertEquals(listOf("published_long", "published_text"), outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG, DataType.STRING), outputs.map { it.executionType })
        assertEquals(astOutputs.map { it.ExecutionType }, outputs.map { it.executionType })
        assertEquals(astOutputs.map { it.ColumnAlias }, outputs.map { it.publishedSqlAlias })
        assertTrue(outputs.all { it.logicalOutputName != it.publishedSqlAlias })
        assertNotEquals(outputs[0].logicalOutputName, outputs[1].logicalOutputName)
    }

    @Test
    fun `provider reports dependency CTE metadata in dependency first order`() {
        val base = cte("metadata_base") {
            select { addColumn { column { tableAttribute("1") }; alias("base_value") } }
        }
        val dependent = cte("metadata_dependent") {
            fromCteInternal(base)
            select { addColumn { cteOutput(base, "base_value"); alias("dependent_value") } }
        }

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(dependent) } }

        val relations = assertIs<EffectiveQueryMetadataProvider>(query)
            .effectiveQueryMetadata()
            .relations

        assertEquals(listOf("metadata_base", "metadata_dependent"), relations.map { it.cteRuntimeName })
        assertEquals(listOf("metadata_base", "metadata_dependent"), relations.map { it.withSqlName })
        assertEquals(listOf("base_value", "dependent_value"), relations.map { it.outputs.single().publishedSqlAlias })
    }
}
