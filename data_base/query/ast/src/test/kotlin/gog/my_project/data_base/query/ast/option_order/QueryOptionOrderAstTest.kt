package gog.my_project.data_base.query.ast.option_order

import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_order.QueryOptionOrderAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryOptionOrderAstTest {

    @Test
    fun `order type delegates to metadata while order columns remain in AST`() {
        val ast = QueryOptionOrderAst()
        val firstColumn = QueryColumnsBaseAst()
        val secondColumn = QueryColumnsBaseAst()

        assertNull(ast.metadata.orderType)
        assertNull(ast.metadata.orderType)

        ast.metadata.orderType = "DESC"
        assertEquals("DESC", ast.metadata.orderType)

        ast.metadata.orderType = "ASC"
        assertEquals("ASC", ast.metadata.orderType)

        ast.orderByList.add(firstColumn)
        ast.orderByList.add(secondColumn)

        assertSame(firstColumn, ast.orderByList[0])
        assertSame(secondColumn, ast.orderByList[1])
    }
}
