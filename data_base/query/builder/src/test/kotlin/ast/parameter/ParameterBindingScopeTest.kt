package gog.my_project.data_base.query.builder.ast.parameter

import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ParameterBindingScopeTest {

    @Test
    fun `equivalent parameters reuse one canonical reference only within their scope`() {
        val firstScope = ParameterBindingScope()
        val first = firstScope.canonical(parameter("value", 11, Types.INTEGER))
        val repeated = firstScope.canonical(parameter("value", 11, Types.INTEGER))
        val secondScope = ParameterBindingScope()
        val independent = secondScope.canonical(parameter("value", 11, Types.INTEGER))

        assertSame(first, repeated)
        assertNotSame(first, independent)
    }

    @Test
    fun `same-name values and SQL types keep independent AST definitions`() {
        val scope = ParameterBindingScope()
        val first = scope.canonical(parameter("value", 11, Types.INTEGER))
        val differentValue = scope.canonical(parameter("value", 22, Types.INTEGER))
        val differentType = scope.canonical(parameter("value", 11, Types.VARCHAR))

        assertNotSame(first, differentValue)
        assertNotSame(first, differentType)
        assertEquals(listOf(11, 22, 11), listOf(first, differentValue, differentType).map { it.metadata.value })
        assertEquals(
            listOf(Types.INTEGER, Types.INTEGER, Types.VARCHAR),
            listOf(first, differentValue, differentType).map { it.metadata.sqlType },
        )
        assertIs<IQueryParameterAst<*>>(first)
    }

    private fun parameter(name: String, value: Any?, sqlType: Int): IQueryParameterAst<Any?> =
        QueryParameterAst<Any?>().apply {
            metadata.name = name
            metadata.value = value
            metadata.sqlType = sqlType
        }
}
