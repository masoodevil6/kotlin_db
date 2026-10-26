package gog.my_project.data_base.query.ast.option_limit

import gog.my_project.data_base.query.ast.schema.select_schema.option_limit.QueryOptionLimitAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryOptionLimitAstTest {

    @Test
    fun `page limit delegates to metadata`() {
        val ast = QueryOptionLimitAst()

        assertNull(ast.metadata.pageLimit)
        assertNull(ast.metadata.pageLimit)

        ast.metadata.pageLimit = 25L
        assertEquals(25L, ast.metadata.pageLimit)

        ast.metadata.pageLimit = 50L
        assertEquals(50L, ast.metadata.pageLimit)
    }
}
