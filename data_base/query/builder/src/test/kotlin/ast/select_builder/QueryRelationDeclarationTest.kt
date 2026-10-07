package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.relations.BuiltQueryRelation
import gog.my_project.data_base.query.builder.relations.QueryRelationDeclaration
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class SampleQueryRelationDeclaration :
    QueryRelationDeclaration<SampleQueryRelationDeclaration.Params>() {

    class Params

    override val relationName = "sample_relation"

    val id: Int
        get() = error("Typed output property values must not be read")

    override fun queryRelation(params: Params): QueryRelation = buildQueryRelation {
        table { table("sample_table").alias("sample") }
        select {
            addColumn {
                column { tableColumn("sample", "id") }
                alias(SampleQueryRelationDeclaration::id)
            }
        }
    }
}

class QueryRelationDeclarationTest {
    @Test
    fun `protected builder uses relation name and concrete owner with typed validation intact`() {
        val relation = SampleQueryRelationDeclaration().queryRelation(
            SampleQueryRelationDeclaration.Params(),
        )

        assertEquals("sample_relation", relation.name)
        assertEquals(
            SampleQueryRelationDeclaration::class,
            (relation as BuiltQueryRelation).declarationOwner,
        )
        assertEquals("id", relation.definition.ast.select!!.columns.single().ColumnAlias)
        assertTrue(MySqlDialect().render(relation.definition.ast)!!.contains("As id"))

        val consumer = QueryRenderSelectBuilder()
            .from(relation)
            .select {
                addColumn { relationColumn(SampleQueryRelationDeclaration::id) }
            }
        val selected = consumer.ast.select!!.columns.single().Column!!
        assertEquals("sample_relation", selected.cteAlias)
        assertEquals("id", selected.select)
    }
}
