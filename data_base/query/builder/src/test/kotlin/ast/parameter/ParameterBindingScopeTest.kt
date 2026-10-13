package gog.my_project.data_base.query.builder.ast.parameter

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ParameterBindingScopeTest {

    @Test
    fun `equivalent parameters reuse one canonical reference only within their scope`() {
        val firstScope = ParameterBindingScope()
        val first = firstScope.canonical(SqlParameter.of("value", 11))
        val repeated = firstScope.canonical(SqlParameter.of("value", 11))
        val secondScope = ParameterBindingScope()
        val independent = secondScope.canonical(SqlParameter.of("value", 11))

        assertSame(first, repeated)
        assertNotSame(first, independent)
    }

    @Test
    fun `same-scope conflicting values and SQL types are rejected`() {
        val valueScope = ParameterBindingScope()
        valueScope.canonical(SqlParameter.of("value", 11))
        assertFailsWith<IllegalArgumentException> {
            valueScope.canonical(SqlParameter.of("value", 22))
        }

        val typeScope = ParameterBindingScope()
        typeScope.canonical(SqlParameter.nullOf("nullable", Types.INTEGER))
        assertFailsWith<IllegalArgumentException> {
            typeScope.canonical(SqlParameter.nullOf("nullable", Types.VARCHAR))
        }
    }
}
