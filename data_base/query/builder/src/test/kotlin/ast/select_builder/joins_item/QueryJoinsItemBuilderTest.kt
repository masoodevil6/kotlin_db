package gog.my_project.data_base.query.builder.ast.select_builder.joins_item

import gog.my_project.data_base.query.api.tools.enums.SqlTypeJoin
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryJoinsItemBuilderTest {

    @Test
    fun `join type is stored in metadata while child ASTs and shared parameter state remain intact`() {
        val parameterBindingScope = ParameterBindingScope()
        assertEquals("param0", parameterBindingScope.nextParameterName())

        val ast = QueryJoinsItemAst()
        val originalTable = ast.joinTable
        val originalConditions = ast.joinConditions
        val builder = QueryJoinsItemBuilder(ast, parameterBindingScope)

        assertNull(ast.metadata.joinType)
        builder.typeJoin(SqlTypeJoin.LeftJoin)
        assertEquals(SqlTypeJoin.LeftJoin.value, ast.metadata.joinType)
        assertEquals(SqlTypeJoin.LeftJoin.value, ast.metadata.joinType)

        builder.innerJoin()
        assertEquals(SqlTypeJoin.InnerJoin.value, ast.metadata.joinType)
        builder.leftJoin()
        assertEquals(SqlTypeJoin.LeftJoin.value, ast.metadata.joinType)
        builder.rightJoin()
        assertEquals(SqlTypeJoin.RightJoin.value, ast.metadata.joinType)

        builder.table { table("details").alias("d") }
        builder.condition {
            addCondition {
                logicalOn()
                sideSelector { tableColumn("d", "owner_id") }
                operationEqual()
                sideValueParam { paramValue(7) }
            }
            addCondition {
                logicalOn()
                sideSelector { tableColumn("d", "owner_id") }
                operationEqual()
                sideValueParam {
                    paramName("legacy_owner_id")
                    paramValue(8)
                }
            }
        }

        assertNotSame(originalTable, ast.joinTable)
        assertEquals("details", ast.joinTable.metadata.nameFromAst)
        assertEquals("d", ast.joinTable.metadata.aliasFromAst)
        assertNotSame(originalConditions, ast.joinConditions)

        val conditions = ast.joinConditions.conditions.map { assertIs<IQueryConditionsAst>(it) }
        val parameters = conditions.map { condition ->
            assertIs<IQueryParameterAst<*>>(condition.conditionSideRight)
        }
        assertEquals(listOf("param1", "legacy_owner_id"), parameters.map { it.metadata.name })
        assertEquals(listOf(7, 8), parameters.map { it.metadata.value })
    }
}
