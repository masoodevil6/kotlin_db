package gog.my_project.data_base.migration.example.v1.migrations.rename_table

import gog.my_project.data_base.migration.builder.renameTable
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class A1ExampleRenameTableV1Test {

    @Test
    fun rendersRenameTable() {
        val sql = MySqlDialect().render(
            A1ExampleRenameTableV1().migration().ast,
        )

        assertEquals("RENAME TABLE `users` TO `customers`", sql)
    }

    @Test
    fun trimsSourceAndTargetNames() {
        val migration = renameTable {
            fromTableName("  users  ")
            toTableName("  customers  ")
        }

        assertEquals(
            "RENAME TABLE `users` TO `customers`",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun escapesMySqlIdentifiers() {
        val migration = renameTable {
            fromTableName("old`table")
            toTableName("new`table")
        }

        assertEquals(
            "RENAME TABLE `old``table` TO `new``table`",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun rejectsBlankSourceName() {
        assertFailsWith<IllegalArgumentException> {
            renameTable { fromTableName("   ") }
        }
    }

    @Test
    fun rejectsBlankTargetName() {
        assertFailsWith<IllegalArgumentException> {
            renameTable { toTableName("   ") }
        }
    }

    @Test
    fun keepsIdentifierParametersEmpty() {
        val migration = A1ExampleRenameTableV1().migration()

        assertEquals(0, migration.params.size)
    }
}