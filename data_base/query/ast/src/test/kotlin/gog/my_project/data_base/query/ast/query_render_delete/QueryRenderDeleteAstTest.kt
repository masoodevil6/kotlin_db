package gog.my_project.data_base.query.ast.query_render_delete

import gog.my_project.data_base.query.ast.metadata.delete.query_render_delete.QueryRenderDeleteAstMetadata
import gog.my_project.data_base.query.ast.schema.delete_schema.query_render_delete.QueryRenderDeleteAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class QueryRenderDeleteAstTest {

    @Test
    fun `targets delegate to current metadata and preserve list contents`() {
        val ast = QueryRenderDeleteAst()

        assertEquals(emptyList(), ast.metadata.targets)
        assertSame(ast.metadata.targets, ast.metadata.targets)

        ast.metadata.targets.add("users")
        ast.metadata.targets.add("profiles")
        assertEquals(listOf("users", "profiles"), ast.metadata.targets)

        val replacementTargets = mutableListOf("accounts")
        ast.metadata.targets = replacementTargets
        assertSame(replacementTargets, ast.metadata.targets)
        assertSame(replacementTargets, ast.metadata.targets)
        assertEquals(listOf("accounts"), ast.metadata.targets)

        val replacementMetadata = QueryRenderDeleteAstMetadata().apply {
            targets = mutableListOf("archive")
        }
        ast.metadata = replacementMetadata

        assertSame(replacementMetadata.targets, ast.metadata.targets)
        assertEquals(listOf("archive"), ast.metadata.targets)
    }
}
