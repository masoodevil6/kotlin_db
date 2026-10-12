package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.fromCteInternal
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteDeclaration
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@QBQuery(name = "sample_declaration", alias = "sample_source")
private class SampleQueryDeclaration(params: Params) :
    CteDeclaration<SampleQueryDeclaration.Params>(params) {

    class Params
    var receivedParams: Params? = null

    val id: Int
        get() = error("Typed output property values must not be read")

    override fun createCte(params: Params): Cte {
        receivedParams = params
        return buildCte {
            from { table("sample_table").alias("sample") }
            select {
                addColumn {
                    column { tableColumn("sample", "id") }
                    alias(SampleQueryDeclaration::id)
                }
            }
        }
    }
}

private class ConventionQuery : CteDeclaration<Unit>(Unit) {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

@QBQuery(name = "metadata_base_cte", alias = "base_source")
private class MetadataBaseQuery : CteDeclaration<Unit>(Unit) {
    val id: Int get() = error("Typed output getter must not be read")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(MetadataBaseQuery::id)
            }
        }
    }
}

@QBQuery(name = "metadata_root_cte", alias = "root_source")
private class MetadataRootQuery : CteDeclaration<Unit>(Unit) {
    val id: Int get() = error("Typed output getter must not be read")

    override fun createCte(params: Unit): Cte = buildCte {
        from { cte(MetadataBaseQuery()) }
        select {
            addColumn {
                cteOutput(MetadataBaseQuery::id)
                alias(MetadataRootQuery::id)
            }
        }
    }
}

class QueryDeclarationTest {
    @Test
    fun `query table API passes the QueryDeclaration constructor parameter exactly once`() {
        val boundParams = SampleQueryDeclaration.Params()
        val declaration = SampleQueryDeclaration(boundParams)

        val query = QueryRenderSelectBuilder()
            .from { cte(declaration) }
            .select { addColumn { cteOutput(SampleQueryDeclaration::id) } }

        assertSame(boundParams, declaration.params)
        assertSame(boundParams, declaration.receivedParams)
        assertEquals(null, query.ast.table!!.cte)
        assertEquals("sample_source", query.ast.table!!.tableAlias)
        assertTrue(query.ast.table!!.derivedQuery != null)
        assertEquals("id", query.ast.table!!.derivedQuery!!.select!!.columns.single().ColumnAlias)
        assertEquals("sample_source", query.ast.select!!.columns.single().Column!!.cteAlias)
        assertEquals("id", query.ast.select!!.columns.single().Column!!.select)
        assertEquals(SampleQueryDeclaration::id, query.ast.select!!.columns.single().PropertyReference)

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(Regex("FROM\\s+\\(\\s*SELECT", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertTrue(sql.contains("As sample_source"), sql)
        assertTrue(sql.contains("sample_source.id"), sql)
        assertTrue(!sql.contains("sample_declaration AS"), sql)
    }

    @Test
    fun `protected builder falls back to class convention when query name is unset`() {
        val relation = ConventionQuery().createCte(Unit)

        assertEquals("convention", relation.name)
        assertEquals(ConventionQuery::class, (relation as BuiltCteComposition).declarationOwner)
    }

    @Test
    fun `declaration with a nested declaration source preserves the nested source inline`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(MetadataRootQuery()) }
            .select { addColumn { cteOutput(MetadataRootQuery::id) } }

        val rootDefinition = query.ast.table!!.derivedQuery!!
        val nestedBaseSource = rootDefinition.table!!
        val sql = MySqlDialect().render(query.ast)!!

        assertEquals(null, query.ast.withs)
        assertEquals(null, query.ast.table!!.cte)
        assertEquals("root_source", query.ast.table!!.tableAlias)
        assertEquals(null, nestedBaseSource.cte)
        assertEquals("base_source", nestedBaseSource.tableAlias)
        assertTrue(nestedBaseSource.derivedQuery != null)
        assertTrue(Regex("FROM\\s+\\(\\s*SELECT", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertTrue(sql.contains("As base_source"), sql)
        assertTrue(sql.contains("As root_source"), sql)
        assertTrue(!sql.contains("metadata_root_cte AS"), sql)
        assertTrue(!sql.contains("metadata_base_cte AS"), sql)
        assertTrue(sql.contains("base_source.id"), sql)
        assertTrue(sql.contains("root_source.id"), sql)
    }

    @Test
    fun `protected builder resolves query name and retains concrete owner with typed validation intact`() {
        val declaration = SampleQueryDeclaration(SampleQueryDeclaration.Params())
        val relation = declaration.createCte(declaration.params)

        assertEquals("sample_declaration", relation.name)
        assertEquals(
            SampleQueryDeclaration::class,
            (relation as BuiltCteComposition).declarationOwner,
        )
        assertEquals("id", relation.definition.ast.select!!.columns.single().ColumnAlias)
        assertTrue(MySqlDialect().render(relation.definition.ast)!!.contains("As id"))

        val consumer = QueryRenderSelectBuilder()
            .fromCteInternal(relation)
            .select {
                addColumn { cteOutput(SampleQueryDeclaration::id) }
            }
        val selected = consumer.ast.select!!.columns.single().Column!!
        assertEquals("sample_source", selected.cteAlias)
        assertEquals("id", selected.select)
    }
}
