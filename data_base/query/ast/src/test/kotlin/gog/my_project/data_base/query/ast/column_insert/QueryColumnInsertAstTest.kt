package gog.my_project.data_base.query.ast.column_insert

import gog.my_project.data_base.query.ast.metadata.insert.column_insert.QueryColumnInsertAstMetadata
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryColumnInsertAstTest {

    @Test
    fun `column name and tag delegate to metadata while parameter AST remains attached`() {
        val ast = QueryColumnInsertAst()
        val parameterAst = QueryParameterAst<Any?>()

        assertNull(ast.metadata.columnName)
        assertNull(ast.metadata.columnName)
        assertNull(ast.metadata.columnTag)
        assertNull(ast.metadata.columnTag)

        ast.metadata.columnName = "user_name"
        ast.metadata.columnTag = "user_name_tag"
        assertEquals("user_name", ast.metadata.columnName)
        assertEquals("user_name_tag", ast.metadata.columnTag)

        ast.metadata.columnName = "display_name"
        ast.metadata.columnTag = "display_name_tag"
        assertEquals("display_name", ast.metadata.columnName)
        assertEquals("display_name_tag", ast.metadata.columnTag)

        ast.parameterAst = parameterAst
        assertSame(parameterAst, ast.parameterAst)

        val replacementMetadata = QueryColumnInsertAstMetadata().apply {
            columnName = "replacement_name"
            columnTag = "replacement_tag"
        }
        ast.metadata = replacementMetadata
        assertEquals("replacement_name", ast.metadata.columnName)
        assertEquals("replacement_tag", ast.metadata.columnTag)
    }
}
