package gog.my_project.data_base.query.ast.table

import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryTableAstTest {

    @Test
    fun `source metadata keeps each layer independent while derived query remains on AST`() {
        val ast = QueryTableAst()
        val derivedQuery = QueryRenderSelectAst()

        assertNull(ast.metadata.nameFromModel)
        assertNull(ast.metadata.nameFromAnnotation)
        assertNull(ast.metadata.nameFromAst)
        assertNull(ast.metadata.aliasFromModel)
        assertNull(ast.metadata.aliasFromAnnotation)
        assertNull(ast.metadata.aliasFromAst)

        ast.metadata.nameFromModel = "user"
        ast.metadata.nameFromAnnotation = "user_users"
        ast.metadata.nameFromAst = "users_override"
        ast.metadata.aliasFromModel = "user"
        ast.metadata.aliasFromAnnotation = "uu"
        ast.metadata.aliasFromAst = "u"
        ast.derivedQuery = derivedQuery

        assertEquals("user", ast.metadata.nameFromModel)
        assertEquals("user_users", ast.metadata.nameFromAnnotation)
        assertEquals("users_override", ast.metadata.nameFromAst)
        assertEquals("user", ast.metadata.aliasFromModel)
        assertEquals("uu", ast.metadata.aliasFromAnnotation)
        assertEquals("u", ast.metadata.aliasFromAst)
        assertEquals("users_override", ast.metadata.resolveName())
        assertEquals("u", ast.metadata.resolveAlias())
        assertSame(derivedQuery, ast.derivedQuery)

        ast.metadata.nameFromAst = null
        ast.metadata.aliasFromAst = null
        assertEquals("user_users", ast.metadata.resolveName())
        assertEquals("uu", ast.metadata.resolveAlias())
        assertSame(derivedQuery, ast.derivedQuery)
    }
}
