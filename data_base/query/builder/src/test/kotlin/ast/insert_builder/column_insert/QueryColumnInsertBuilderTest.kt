package gog.my_project.data_base.query.builder.ast.insert_builder.column_insert

import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.builder.ast.insert_builder.column_insert.QueryColumnInsertBuilder
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class QueryColumnInsertBuilderTest {
    @Test
    fun `setValue stores independent parameter AST without legacy params`() {
        val builder = QueryColumnInsertBuilder()
        builder.column("display_name").setValue {
            paramName("insert_display_name")
            paramValue("Ada")
        }

        assertEquals("display_name", builder.ast.metadata.columnName)
        assertEquals("display_name", builder.ast.metadata.columnTag)
        val parameter = assertIs<IQueryParameterAst<*>>(builder.ast.parameterAst)
        assertEquals("insert_display_name", parameter.metadata.name)
        assertEquals("Ada", parameter.metadata.value)
        assertEquals(Types.VARCHAR, parameter.metadata.sqlType)
    }

    @Test
    fun `setValue defaults parameter name to the existing column tag`() {
        val builder = QueryColumnInsertBuilder()
        builder.column("user_id").setValue { paramValue(42) }

        val parameter = assertIs<IQueryParameterAst<*>>(builder.ast.parameterAst)
        assertEquals("user_id", parameter.metadata.name)
        assertEquals(42, parameter.metadata.value)
        assertEquals(Types.INTEGER, parameter.metadata.sqlType)
    }

    @Test
    fun `setValue requires a column and a value definition`() {
        val missingColumn = QueryColumnInsertBuilder()
        assertFailsWith<IllegalStateException> {
            missingColumn.setValue { paramValue(1) }
        }

        val missingValue = QueryColumnInsertBuilder()
        missingValue.column("user_id")
        assertFailsWith<IllegalStateException> {
            missingValue.setValue { paramName("user_id") }
        }
        assertEquals(null, missingValue.ast.parameterAst)
    }
}
