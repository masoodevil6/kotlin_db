package gog.my_project.data_base.query.ast.option_offset

import gog.my_project.data_base.query.ast.schema.select_schema.option_offset.QueryOptionOffsetAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryOptionOffsetAstTest {

    @Test
    fun `page offset delegates to metadata`() {
        val ast = QueryOptionOffsetAst()

        assertNull(ast.metadata.pageOffset)
        assertNull(ast.metadata.pageOffset)

        ast.metadata.pageOffset = 25L
        assertEquals(25L, ast.metadata.pageOffset)

        ast.metadata.pageOffset = 50L
        assertEquals(50L, ast.metadata.pageOffset)
    }
}
