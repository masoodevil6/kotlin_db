package gog.my_project.data_base.migration.example.v1.migrations.rename_table

import gog.my_project.data_base.migration.builder.ast.rename_table.MigrationRenameTableBuilder
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




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
        val migration = MigrationRenameTableBuilder().apply {
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
        val migration = MigrationRenameTableBuilder().apply {
            fromTableName("old`table")
            toTableName("new`table")
        }

        assertEquals(
            "RENAME TABLE `old``table` TO `new``table`",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun rendersBlankSourceNameForDatabaseValidation() {
        val migration = MigrationRenameTableBuilder().apply { fromTableName("   "); toTableName("customers") }
        assertEquals("RENAME TABLE `` TO `customers`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rendersBlankTargetNameForDatabaseValidation() {
        val migration = MigrationRenameTableBuilder().apply { fromTableName("users"); toTableName("   ") }
        assertEquals("RENAME TABLE `users` TO ``", MySqlDialect().render(migration.ast))
    }

    @Test
    fun keepsIdentifierParametersEmpty() {
        val migration = A1ExampleRenameTableV1().migration()

        assertEquals(0, migration.params.size)
    }
}
