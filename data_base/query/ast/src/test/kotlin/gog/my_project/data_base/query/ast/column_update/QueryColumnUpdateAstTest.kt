package gog.my_project.data_base.query.ast.column_update

import gog.my_project.data_base.query.ast.metadata.update.column_update.QueryColumnUpdateAstMetadata
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryColumnUpdateAstTest {

    @Test
    fun `column configuration delegates to metadata and parameter AST remains attached`() {
        val ast = QueryColumnUpdateAst()
        val parameterAst = QueryParameterAst<Any?>()

        assertNull(ast.metadata.columnAlias)
        assertNull(ast.metadata.columnAlias)
        assertNull(ast.metadata.columnName)
        assertNull(ast.metadata.columnName)
        assertNull(ast.metadata.columnTag)
        assertNull(ast.metadata.columnTag)

        ast.metadata.columnAlias = "ua"
        ast.metadata.columnName = "amount"
        ast.metadata.columnTag = "ua_amount"
        assertEquals("ua", ast.metadata.columnAlias)
        assertEquals("amount", ast.metadata.columnName)
        assertEquals("ua_amount", ast.metadata.columnTag)

        ast.metadata.columnAlias = "replacement_alias"
        ast.metadata.columnName = "replacement_amount"
        ast.metadata.columnTag = "replacement_tag"
        assertEquals("replacement_alias", ast.metadata.columnAlias)
        assertEquals("replacement_amount", ast.metadata.columnName)
        assertEquals("replacement_tag", ast.metadata.columnTag)

        ast.parameterAst = parameterAst
        assertSame(parameterAst, ast.parameterAst)

        ast.metadata = QueryColumnUpdateAstMetadata().apply {
            columnAlias = "new_alias"
            columnName = "new_name"
            columnTag = "new_tag"
        }
        assertEquals("new_alias", ast.metadata.columnAlias)
        assertEquals("new_name", ast.metadata.columnName)
        assertEquals("new_tag", ast.metadata.columnTag)
    }
}
