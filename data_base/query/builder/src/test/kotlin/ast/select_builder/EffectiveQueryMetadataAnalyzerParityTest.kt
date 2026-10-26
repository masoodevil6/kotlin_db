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
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.meta_data.analysis.EffectiveQueryMetadataAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals

@QBQuery(name = "analyzer_declaration", alias = "analyzer_default_source")
private class AnalyzerDeclaration : ICteDeclaration<Unit> {
    @QBQuerySelect(name = "logical_analyzer_id", alias = "published_analyzer_id")
    val id: Long
        get() = error("Metadata analysis must not invoke output getters")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(AnalyzerDeclaration::id)
                execute(DataType.LONG)
            }
        }
    }
}

@QBTable(name = "analyzer_model_table", alias = "am")
private class AnalyzerModel : IModelBase {
    @QBColumn(name = "id")
    val id: Long
        get() = error("Metadata analysis must not invoke model property getters")
}

class EffectiveQueryMetadataAnalyzerTest {
    private val analyzer = EffectiveQueryMetadataAnalyzer()

    @Test
    fun `analyzer matches model-backed and raw expression outputs without reading getters`() {
        val query = QueryRenderSelectBuilder()
            .from { model(AnalyzerModel::class).alias("model_source") }
            .select {
                addColumn {
                    column { tableSelector(AnalyzerModel::class); columnSelector(AnalyzerModel::class, AnalyzerModel::id) }
                    alias("published_id")
                }
                addColumn {
                    column { tableAttribute("model_source.id + 1") }
                    alias("computed_id")
                    execute(DataType.LONG)
                }
            }

        val outputs = analyzer.analyze(query.ast).outputs
        assertEquals("analyzer_model_table", outputs[0].tableName)
        assertEquals("model_source", outputs[0].tableAlias)
        assertEquals("id", outputs[0].logicalOutputName)
        assertEquals(DataType.LONG, outputs[0].executionType)
        assertEquals(null, outputs[1].tableName)
        assertEquals(null, outputs[1].tableAlias)
        assertEquals("computed_id", outputs[1].publishedSqlAlias)
        assertEquals(DataType.LONG, outputs[1].executionType)
    }

    @Test
    fun `analyzer matches dependency closure explicit roots override raw WITH and source occurrence`() {
        val base = cte("analyzer_base") {
            select { addColumn { column { tableAttribute("1") }; alias("base_value") } }
        }
        val shared = cte("analyzer_shared") {
            select { addColumn { column { tableAttribute("2") }; alias("shared_value") } }
        }
        val rootDefinition = cte("analyzer_root") {
            select { addColumn { column { tableAttribute("3") }; alias("root_value") } }
        }
        val root = object : Cte by rootDefinition {
            override val dependencies: List<Cte> = listOf(base, shared)
        }

        val query = QueryRenderSelectBuilder()
            .withs {
                addWith { with("renamed_analyzer_base", base) }
                addWith { with(root) }
                addWith { with(root) }
                addWith {
                    with("raw_analyzer_with") {
                        select { addColumn { column { tableAttribute("4") }; alias("raw_value") } }
                    }
                }
            }
            .from { cte(root.name).alias("root_occurrence") }
            .select {
                addColumn {
                    column { cteColumn(root, "root_value") }
                    alias("answer")
                    execute(DataType.LONG)
                }
            }

        val metadata = analyzer.analyze(query.ast)
        assertEquals(
            listOf("analyzer_base", "analyzer_shared", "analyzer_root", "raw_analyzer_with", "analyzer_root"),
            metadata.relations.mapNotNull { it.cteRuntimeName ?: it.withSqlName },
        )
        assertEquals("root_occurrence", metadata.relations.last().occurrenceAlias)
        assertEquals("analyzer_root", metadata.outputs.single().tableName)
        assertEquals("root_occurrence", metadata.outputs.single().tableAlias)
    }

    @Test
    fun `analyzer matches typed CTE names and inline CTE source without reading getters`() {
        val declaration = AnalyzerDeclaration()
        val explicitCte = declaration.createCte(Unit)
        val withQuery = QueryRenderSelectBuilder()
            .withs { addWith { with("published_analyzer_with", explicitCte) } }
        val inlineQuery = QueryRenderSelectBuilder()
        CteAstComposer(inlineQuery.ast, ModelTableAliasContext(), CteSourceContext())
            .addInlineCteSource(explicitCte, "inline_analyzer_source")

        val withRelation = analyzer.analyze(withQuery.ast).relations.single()
        assertEquals("analyzer_declaration", withRelation.logicalQueryName)
        assertEquals("analyzer_declaration", withRelation.cteRuntimeName)
        assertEquals("published_analyzer_with", withRelation.withSqlName)
        assertEquals("logical_analyzer_id", withRelation.outputs.single().logicalOutputName)
        assertEquals("published_analyzer_id", withRelation.outputs.single().publishedSqlAlias)

        val inlineRelation = analyzer.analyze(inlineQuery.ast).relations.single()
        assertEquals("analyzer_declaration", inlineRelation.logicalQueryName)
        assertEquals("analyzer_declaration", inlineRelation.cteRuntimeName)
        assertEquals("inline_analyzer_source", inlineRelation.occurrenceAlias)
        assertEquals("logical_analyzer_id", inlineRelation.outputs.single().logicalOutputName)
    }

    @Test
    fun `analyzer distinguishes a named CTE occurrence from a same-named table source`() {
        val cteSource = QueryRenderSelectBuilder()
            .withs {
                addWith {
                    with("shared_source_name") {
                        select { addColumn { column { tableAttribute("1") }; alias("value") } }
                    }
                }
            }
            .from { cte("shared_source_name").alias("cte_occurrence") }

        val tableSource = QueryRenderSelectBuilder()
            .withs {
                addWith {
                    with("shared_source_name") {
                        select { addColumn { column { tableAttribute("1") }; alias("value") } }
                    }
                }
            }
            .from { table("shared_source_name").alias("table_occurrence") }

        assertEquals(2, analyzer.analyze(cteSource.ast).relations.size)
        assertEquals(1, analyzer.analyze(tableSource.ast).relations.size)
        assertEquals("cte_occurrence", analyzer.analyze(cteSource.ast).relations.last().occurrenceAlias)
    }

    @Test
    fun `analyzer preserves JOIN alias ambiguity unresolved sources and derived SELECT output`() {
        val ambiguousQuery = QueryRenderSelectBuilder()
            .from { table("users").alias("same") }
            .joins {
                addJoin {
                    innerJoin()
                    table { table("posts").alias("same") }
                }
            }
            .select {
                addColumn { column { tableColumn("same", "id") }; alias("ambiguous_id") }
                addColumn { column { tableColumn("missing", "id") }; alias("unresolved_id") }
            }

        val derivedQuery = QueryRenderSelectBuilder()
            .from {
                table {
                    select { addColumn { column { tableAttribute("1") }; alias("id") } }
                }.alias("derived_source")
            }
            .select {
                addColumn { column { tableColumn("derived_source", "id") }; alias("derived_id") }
            }

        assertEquals(null, analyzer.analyze(ambiguousQuery.ast).outputs[0].tableName)
        assertEquals("same", analyzer.analyze(ambiguousQuery.ast).outputs[0].tableAlias)
        assertEquals(null, analyzer.analyze(ambiguousQuery.ast).outputs[1].tableName)
        assertEquals("missing", analyzer.analyze(ambiguousQuery.ast).outputs[1].tableAlias)
        assertEquals(null, analyzer.analyze(derivedQuery.ast).outputs.single().tableName)
        assertEquals("derived_source", analyzer.analyze(derivedQuery.ast).outputs.single().tableAlias)
    }
}
