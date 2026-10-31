package gog.my_project.data_base.migration.example.v1.migrations.drop_column

import gog.my_project.data_base.migration.ast.schema.drop_column.MigrationDropColumnAst
import gog.my_project.data_base.migration.builder.ast.drop_column.MigrationDropColumnBuilder
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




class A1ExampleDropColumnV1Test {
    @Test
    fun rendersDropColumnForMySql() {
        val sql = MySqlDialect().render(A1ExampleDropColumnV1().migration().ast)

        assertEquals("ALTER TABLE `users` DROP COLUMN `display_name`", sql)
    }

    @Test
    fun ifExistsDefaultsToFalseAndCanBeEnabledInAst() {
        val ordinary = A1ExampleDropColumnV1().migration()
        val optional = A1ExampleDropColumnV1(useIfExists = true).migration()

        assertEquals(false, ordinary.ast.metadata.ifExists)
        assertEquals(true, optional.ast.metadata.ifExists)
        // MySQL does not accept DROP COLUMN IF EXISTS in ALTER TABLE syntax.
        assertEquals(
            "ALTER TABLE `users` DROP COLUMN `display_name`",
            MySqlDialect().render(optional.ast),
        )
    }

    @Test
    fun builderTrimsAndStoresIdentifiersInAst() {
        val migration = MigrationDropColumnBuilder().apply {
            tableName("  users  ")
            name("  email  ")
        }

        assertEquals("users", migration.ast.metadata.tableName)
        assertEquals("email", migration.ast.metadata.name)
        assertEquals("ALTER TABLE `users` DROP COLUMN `email`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rendersBlankTableNameForDatabaseValidation() {
        val migration = MigrationDropColumnBuilder().apply { tableName("   "); name("email") }
        assertEquals("ALTER TABLE `` DROP COLUMN `email`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rendersBlankColumnNameForDatabaseValidation() {
        val migration = MigrationDropColumnBuilder().apply { tableName("users"); name("   ") }
        assertEquals("ALTER TABLE `users` DROP COLUMN ``", MySqlDialect().render(migration.ast))
    }

    @Test
    fun defersMissingTableNameToRenderer() {
        val migration = MigrationDropColumnBuilder().apply { name("email") }
        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun defersMissingColumnNameToRenderer() {
        val migration = MigrationDropColumnBuilder().apply { tableName("users") }
        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun rendererRejectsIncompleteAstBeforeRendering() {
        val ast = MigrationDropColumnAst().apply {
            metadata.tableName = "users"
        }

        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(ast)
        }
    }

    @Test
    fun escapesTableAndColumnIdentifiers() {
        val migration = MigrationDropColumnBuilder().apply {
            tableName("user`data")
            name("display`name")
        }

        assertEquals(
            "ALTER TABLE `user``data` DROP COLUMN `display``name`",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun identifiersDoNotCreateBindParameters() {
        assertEquals(0, A1ExampleDropColumnV1().migration().params.size)
    }
}
