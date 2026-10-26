package gog.my_project.data_base.query.ast.parameter

import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryParameterAstTest {

    @Test
    fun `parameter node stores its definition without a SqlParameter`() {
        val node = QueryParameterAst<Int>()
        node.metadata.name = "minimum_age"
        node.metadata.value = 18
        node.metadata.sqlType = Types.INTEGER

        assertEquals("minimum_age", node.metadata.name)
        assertEquals(18, node.metadata.value)
        assertEquals(Types.INTEGER, node.metadata.sqlType)
        assertEquals(node.metadata.name, node.metadata.name)
        assertEquals(node.metadata.value, node.metadata.value)
        assertEquals(node.metadata.sqlType, node.metadata.sqlType)
    }

    @Test
    fun `parameter AST properties read and write metadata directly`() {
        val node = QueryParameterAst<Int>().apply {
            metadata.name = "initial"
            metadata.value = 1
            metadata.sqlType = Types.INTEGER
        }

        node.metadata.name = "updated"
        node.metadata.value = null
        node.metadata.sqlType = Types.NULL

        assertEquals("updated", node.metadata.name)
        assertEquals(null, node.metadata.value)
        assertEquals(Types.NULL, node.metadata.sqlType)

        val replacementMetadata =
            gog.my_project.data_base.query.ast.metadata.parameter.QueryParameterAstMetadata<Int>().apply {
                name = "replacement"
                value = 2
                sqlType = Types.BIGINT
            }
        node.metadata = replacementMetadata

        assertSame(replacementMetadata, node.metadata)
        assertEquals("replacement", node.metadata.name)
        assertEquals(2, node.metadata.value)
        assertEquals(Types.BIGINT, node.metadata.sqlType)
    }

    @Test
    fun `nullable AST metadata remains unset until assigned`() {
        val node = QueryParameterAst<String>()

        assertNull(node.metadata.name)
        assertNull(node.metadata.value)
        assertNull(node.metadata.sqlType)
    }

    @Test
    fun `parameter node identity is reference based`() {
        val first = QueryParameterAst<Int>().apply {
            metadata.name = "value"
            metadata.value = 11
            metadata.sqlType = Types.INTEGER
        }
        val second = QueryParameterAst<Int>().apply {
            metadata.name = "value"
            metadata.value = 11
            metadata.sqlType = Types.INTEGER
        }

        assertFalse(first === second)
    }
}
