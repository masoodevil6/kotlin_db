package gog.my_project.data_base.query.ast.conditions_group

import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item.QueryConditionsAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryConditionsGroupsAstTest {

    @Test
    fun `logical settings delegate to metadata while child conditions remain in AST`() {
        val ast = QueryConditionsGroupsAst()
        val firstCondition = QueryConditionsAst()
        val secondCondition = QueryConditionsAst()

        assertFalse(ast.metadata.isAddLogical)
        assertFalse(ast.metadata.isAddLogical)
        assertNull(ast.metadata.conditionLogical)
        assertNull(ast.metadata.conditionLogical)

        ast.metadata.isAddLogical = true
        assertEquals(true, ast.metadata.isAddLogical)

        ast.metadata.isAddLogical = false
        assertFalse(ast.metadata.isAddLogical)

        ast.metadata.conditionLogical = "AND"
        assertEquals("AND", ast.metadata.conditionLogical)

        ast.metadata.conditionLogical = "OR"
        assertEquals("OR", ast.metadata.conditionLogical)

        ast.conditions.add(firstCondition)
        ast.conditions.add(secondCondition)

        assertSame(firstCondition, ast.conditions[0])
        assertSame(secondCondition, ast.conditions[1])
    }
}
