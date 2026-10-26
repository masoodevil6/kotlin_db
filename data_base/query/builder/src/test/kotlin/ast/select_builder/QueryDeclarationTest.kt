package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@QBQuery(name = "sample_declaration", alias = "sample_source")
private class SampleQueryDeclaration :
    ICteDeclaration<SampleQueryDeclaration.Params> {

    class Params
    var receivedParams: Params? = null
    var createCalls: Int = 0

    val id: Int
        get() = error("Typed output property values must not be read")

    override fun createCte(params: Params): Cte {
        createCalls++
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

private class ConventionQuery : ICteDeclaration<Unit> {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

@QBQuery(name = "metadata_base_cte", alias = "base_source")
private class MetadataBaseQuery : ICteDeclaration<Unit> {
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
private class MetadataRootQuery : ICteDeclaration<Unit> {
    val id: Int get() = error("Typed output getter must not be read")

    override fun createCte(params: Unit): Cte {
        val base = MetadataBaseQuery().createCte(Unit)
        return buildCte {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select {
                addColumn {
                    column { tableSelector(MetadataBaseQuery::class); columnSelector(MetadataBaseQuery::id) }
                    alias(MetadataRootQuery::id)
                }
            }
        }
    }
}

class QueryDeclarationTest {
    @Test
    fun `query relation receives explicit parameters exactly once and retains typed owner metadata`() {
        val boundParams = SampleQueryDeclaration.Params()
        val declaration = SampleQueryDeclaration()
        val relation = declaration.createCte(boundParams)

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(relation) } }
            .from { cte(relation.name) }
            .select { addColumn { column { tableSelector(SampleQueryDeclaration::class); columnSelector(SampleQueryDeclaration::id) } } }

        assertSame(boundParams, declaration.receivedParams)
        assertEquals(1, declaration.createCalls)
        assertEquals("sample_declaration", query.ast.table!!.metadata.resolveName())
        assertEquals("sample_source", query.ast.table!!.metadata.aliasFromAst)
        assertNull(query.ast.table!!.derivedQuery)
        assertEquals("sample_declaration", query.ast.withs!!.withs.single().metadata.withName)
        assertEquals("id", query.ast.withs!!.withs.single().withBody!!.select!!.columns.single().metadata.ColumnAlias)
        assertEquals("sample_source", query.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())
        assertEquals("id", query.ast.select!!.columns.single().Column!!.metadata.resolveColumnName())
        assertEquals(SampleQueryDeclaration::id, query.ast.select!!.columns.single().metadata.PropertyReference)

        val sql = MySqlDialect().render(query.ast)!!
        val normalizedSql = sql.replace(Regex("\\s+"), " ").lowercase()
        assertTrue(normalizedSql.contains("sample_declaration as"), sql)
        assertTrue(normalizedSql.contains("as sample_source"), sql)
        assertTrue(normalizedSql.contains("sample_source.id"), sql)
    }

    @Test
    fun `query declaration factory falls back to class convention when query name is unset`() {
        val relation = ConventionQuery().createCte(Unit)

        assertEquals("convention", relation.name)
        assertEquals(ConventionQuery::class, (relation as BuiltCteComposition).declarationOwner)
    }

    @Test
    fun `declaration relation composes its nested CTE dependency in WITH order`() {
        val relation = MetadataRootQuery().createCte(Unit)
        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(relation) } }
            .from { cte(relation.name) }
            .select { addColumn { column { tableSelector(MetadataRootQuery::class); columnSelector(MetadataRootQuery::id) } } }

        val withItems = query.ast.withs!!.withs
        val sql = MySqlDialect().render(query.ast)!!

        assertEquals(listOf("metadata_base_cte", "metadata_root_cte"), withItems.map { it.metadata.withName })
        assertEquals("metadata_root_cte", query.ast.table!!.metadata.resolveName())
        assertEquals("root_source", query.ast.table!!.metadata.aliasFromAst)
        val nestedBaseSource = withItems[1].withBody!!.table!!
        assertEquals("metadata_base_cte", nestedBaseSource.metadata.resolveName())
        assertTrue(withItems[1].withBody!!.withs!!.withs.isEmpty())
        val normalizedSql = sql.replace(Regex("\\s+"), " ").lowercase()
        assertTrue(normalizedSql.contains("metadata_base_cte as"), sql)
        assertTrue(normalizedSql.contains("metadata_root_cte as"), sql)
        assertTrue(normalizedSql.contains("as base_source"), sql)
        assertTrue(normalizedSql.contains("as root_source"), sql)
        assertTrue(normalizedSql.contains("base_source.id"), sql)
        assertTrue(normalizedSql.contains("root_source.id"), sql)
    }

    @Test
    fun `protected builder resolves query name and retains concrete owner with typed validation intact`() {
        val declaration = SampleQueryDeclaration()
        val relation = declaration.createCte(SampleQueryDeclaration.Params())

        assertEquals("sample_declaration", relation.name)
        assertEquals(
            SampleQueryDeclaration::class,
            (relation as BuiltCteComposition).declarationOwner,
        )
        assertEquals("id", relation.definition.ast.select!!.columns.single().metadata.ColumnAlias)
        assertTrue(MySqlDialect().render(relation.definition.ast)!!.contains("As id"))

        val consumer = QueryRenderSelectBuilder()
            .from { this.cte(relation) }
            .select {
                addColumn { column { tableSelector(SampleQueryDeclaration::class); columnSelector(SampleQueryDeclaration::id) } }
            }
        val selected = consumer.ast.select!!.columns.single().Column!!
        assertEquals("sample_source", selected.metadata.resolveTableAlias())
        assertEquals("id", selected.metadata.resolveColumnName())
    }
}
