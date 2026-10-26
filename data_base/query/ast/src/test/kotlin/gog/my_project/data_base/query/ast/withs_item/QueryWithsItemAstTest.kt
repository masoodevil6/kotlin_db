package gog.my_project.data_base.query.ast.withs_item

import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryWithsItemAstTest {

    @Test
    fun `with name delegates to metadata while body remains on AST`() {
        val ast = QueryWithsItemAst()
        val withBody = QueryRenderSelectAst()

        assertNull(ast.metadata.withName)
        assertNull(ast.withBody)

        ast.metadata.withName = "user_stats"
        ast.withBody = withBody

        assertEquals("user_stats", ast.metadata.withName)
        assertSame(withBody, ast.withBody)

        ast.metadata.withName = "renamed_user_stats"

        assertEquals("renamed_user_stats", ast.metadata.withName)
        assertSame(withBody, ast.withBody)
    }
}
