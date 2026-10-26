package gog.my_project.data_base.query.ast.joins_item

import gog.my_project.data_base.query.ast.schema.select_schema.joins_item.QueryJoinsItemAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryJoinsItemAstTest {

    @Test
    fun `join type delegates to metadata while child AST references remain on the node`() {
        val ast = QueryJoinsItemAst()
        val joinTable = ast.joinTable
        val joinConditions = ast.joinConditions

        assertNull(ast.metadata.joinType)
        ast.metadata.joinType = "left join"
        assertEquals("left join", ast.metadata.joinType)

        ast.metadata.joinType = "right join"
        assertEquals("right join", ast.metadata.joinType)

        assertNotNull(ast.joinTable)
        assertNotNull(ast.joinConditions)
        assertSame(joinTable, ast.joinTable)
        assertSame(joinConditions, ast.joinConditions)
    }
}
