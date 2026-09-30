package gog.my_project.data_base.migration.example.v1.migrations.drop_column

import gog.my_project.data_base.migration.ast.schema.drop_column.MigrationDropColumnAst
import gog.my_project.data_base.migration.builder.dropColumn
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

        assertEquals(false, ordinary.ast.ifExists)
        assertEquals(true, optional.ast.ifExists)
        // MySQL does not accept DROP COLUMN IF EXISTS in ALTER TABLE syntax.
        assertEquals(
            "ALTER TABLE `users` DROP COLUMN `display_name`",
            MySqlDialect().render(optional.ast),
        )
    }

    @Test
    fun builderTrimsAndStoresIdentifiersInAst() {
        val migration = dropColumn {
            tableName("  users  ")
            name("  email  ")
        }

        assertEquals("users", migration.ast.tableName)
        assertEquals("email", migration.ast.name)
        assertEquals("ALTER TABLE `users` DROP COLUMN `email`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rejectsBlankTableNameInBuilder() {
        assertFailsWith<IllegalArgumentException> {
            dropColumn { tableName("   ") }
        }
    }

    @Test
    fun rejectsBlankColumnNameInBuilder() {
        assertFailsWith<IllegalArgumentException> {
            dropColumn {
                tableName("users")
                name("   ")
            }
        }
    }

    @Test
    fun rejectsMissingTableNameWhenBuilderCompletes() {
        assertFailsWith<IllegalArgumentException> {
            dropColumn { name("email") }
        }
    }

    @Test
    fun rejectsMissingColumnNameWhenBuilderCompletes() {
        assertFailsWith<IllegalArgumentException> {
            dropColumn { tableName("users") }
        }
    }

    @Test
    fun rendererRejectsIncompleteAstBeforeRendering() {
        val ast = MigrationDropColumnAst().apply {
            tableName = "users"
        }

        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(ast)
        }
    }

    @Test
    fun escapesTableAndColumnIdentifiers() {
        val migration = dropColumn {
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
