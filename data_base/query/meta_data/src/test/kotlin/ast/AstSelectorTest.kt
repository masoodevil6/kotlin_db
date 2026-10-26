package gog.my_project.data_base.query.meta_data.ast

import gog.my_project.data_base.query.ast.schema.select_schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.select.QuerySelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AstSelectorTest {
    @Test
    fun `starts with typed root and exposes its metadata`() {
        val root = QueryRenderSelectAst()
        val selection = AstSelector.from(root)

        assertSame(root, selection.nodes.single())
        assertSame(root.metadata, selection.metadata.single())
    }

    @Test
    fun `nullable relationship with no child yields an empty selection`() {
        val selection = AstSelector.from(QueryRenderSelectAst())
            .select { it.select }

        assertTrue(selection.nodes.isEmpty())
        assertTrue(selection.metadata.isEmpty())
    }

    @Test
    fun `select to columns preserves AST order and each column metadata`() {
        val first = QueryColumnsAst()
        val second = QueryColumnsAst()
        val root = QueryRenderSelectAst().apply {
            select = QuerySelectAst().apply { columns.addAll(listOf(first, second)) }
        }

        val selection = AstSelector.from(root)
            .select { it.select }
            .selectMany { it.columns }

        assertEquals(listOf(first, second), selection.nodes)
        assertEquals(listOf(first.metadata, second.metadata), selection.metadata)
    }

    @Test
    fun `joins and with items follow only their selected typed relationships`() {
        val firstTable = QueryTableAst()
        val secondTable = QueryTableAst()
        val firstJoin = QueryJoinsItemAst().apply { joinTable = firstTable }
        val secondJoin = QueryJoinsItemAst().apply { joinTable = secondTable }
        val body = QueryRenderSelectAst()
        val withItem = QueryWithsItemAst().apply { withBody = body }
        val root = QueryRenderSelectAst().apply {
            joins = QueryJoinsAst().apply { joins.addAll(listOf(firstJoin, secondJoin)) }
            withs = QueryWithsAst().apply { withs.add(withItem) }
        }

        val joinTables = AstSelector.from(root)
            .select { it.joins }
            .selectMany { it.joins }
            .select { it.joinTable }
        val withBodies = AstSelector.from(root)
            .select { it.withs }
            .selectMany { it.withs }
            .select { it.withBody }

        assertEquals(listOf(firstTable, secondTable), joinTables.nodes)
        assertEquals(listOf(firstTable.metadata, secondTable.metadata), joinTables.metadata)
        assertEquals(listOf(body), withBodies.nodes)
        assertSame(body.metadata, withBodies.metadata.single())
    }
}
