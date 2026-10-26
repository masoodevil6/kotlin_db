package gog.my_project.data_base.query.builder.ast.parameter

import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueryParameterBuilderTest {

    @Test
    fun `builder completes the AST instance supplied to it`() {
        val ast = QueryParameterAst<Any?>()
        val builder = QueryParameterBuilder(ast) { "unused_default" }

        builder.paramName("id1")
        builder.paramValue(1)

        assertEquals("id1", ast.metadata.name)
        assertEquals(1, ast.metadata.value)
        assertEquals(Types.INTEGER, ast.metadata.sqlType)

        builder.validate()

        assertEquals("id1", ast.metadata.name)
        assertEquals(1, ast.metadata.value)
        assertEquals(Types.INTEGER, ast.metadata.sqlType)
    }

    @Test
    fun `builder applies the default name during validation`() {
        val ast = QueryParameterAst<Any?>()
        val builder = QueryParameterBuilder(ast) { "param7" }

        builder.paramValue(null)
        builder.validate()

        assertEquals("param7", ast.metadata.name)
        assertEquals(null, ast.metadata.value)
        assertEquals(Types.NULL, ast.metadata.sqlType)
    }

    @Test
    fun `builder validates missing and repeated values after DSL calls`() {
        val missingValueAst = QueryParameterAst<Any?>()
        val missingValueBuilder = QueryParameterBuilder(missingValueAst) { "param0" }
        missingValueBuilder.paramName("id1")

        assertFailsWith<IllegalStateException> { missingValueBuilder.validate() }

        val repeatedValueAst = QueryParameterAst<Any?>()
        val repeatedValueBuilder = QueryParameterBuilder(repeatedValueAst) { "param0" }
        repeatedValueBuilder.paramValue(1)
        repeatedValueBuilder.paramValue(2)

        assertFailsWith<IllegalStateException> { repeatedValueBuilder.validate() }
    }

    @Test
    fun `builder rejects repeated names and invalid names during validation`() {
        val repeatedNameAst = QueryParameterAst<Any?>()
        val repeatedNameBuilder = QueryParameterBuilder(repeatedNameAst) { "param0" }
        repeatedNameBuilder.paramName("first")
        repeatedNameBuilder.paramName("second")
        repeatedNameBuilder.paramValue(1)

        assertFailsWith<IllegalStateException> { repeatedNameBuilder.validate() }

        val invalidNameAst = QueryParameterAst<Any?>()
        val invalidNameBuilder = QueryParameterBuilder(invalidNameAst) { "param0" }
        invalidNameBuilder.paramName("bad-name")
        invalidNameBuilder.paramValue(1)

        assertFailsWith<IllegalArgumentException> { invalidNameBuilder.validate() }
    }
}
