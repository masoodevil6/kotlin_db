package gog.my_project.data_base.query.ast.conditions_item

import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryConditionsAstTest {

    @Test
    fun `condition scalar settings delegate to metadata and child references stay in AST`() {
        val ast = QueryConditionsAst()
        val leftColumn = QueryColumnsBaseAst()
        val rightColumn = QueryColumnsBaseAst()

        assertFalse(ast.metadata.isAddLogical)
        assertNull(ast.metadata.conditionLogical)
        assertNull(ast.metadata.conditionOperation)

        ast.metadata.isAddLogical = true
        assertEquals(true, ast.metadata.isAddLogical)

        ast.metadata.conditionLogical = "AND"
        assertEquals("AND", ast.metadata.conditionLogical)
        ast.metadata.conditionLogical = "OR"
        assertEquals("OR", ast.metadata.conditionLogical)

        ast.metadata.conditionOperation = "="
        assertEquals("=", ast.metadata.conditionOperation)
        ast.metadata.conditionOperation = "LIKE"
        assertEquals("LIKE", ast.metadata.conditionOperation)

        ast.conditionSideLeft = leftColumn
        ast.conditionSideRight = rightColumn
        assertSame(leftColumn, ast.conditionSideLeft)
        assertSame(rightColumn, ast.conditionSideRight)
    }
}
