package gog.my_project.data_base.query.renderer.lock

import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockForUpdateRenderingTest {

    @Test
    fun `MySQL select renders FOR UPDATE only when lockForUpdate is requested`() {
        val dialect = MySqlDialect()
        val withoutLock = requireNotNull(dialect.render(selectQuery().ast))
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
        val withLock = requireNotNull(
            dialect.render(selectQuery().apply { lockForUpdate {} }.ast),
        )
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        assertFalse(withoutLock.contains("for update"), withoutLock)
        assertTrue(withLock.endsWith("for update"), withLock)
        assertEquals("$withoutLock for update", withLock)
    }

    private fun selectQuery() = QueryRenderSelectBuilder().apply {
        select {
            addColumn {
                column { columnSelector("1") }
                alias("value")
            }
        }
        from { table("users").alias("u") }
    }
}
