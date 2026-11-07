package gog.my_project.data_base.migration.example.v1.migrations.drop_column

import gog.my_project.data_base_schema.SchemaDialect

import gog.my_project.data_base_schema.Schema


import gog.my_project.data_base_schema.ast.schema.drop_column.SchemaDropColumnAst
import gog.my_project.data_base_schema.builder.ast.drop_column.SchemaDropColumnBuilder
import gog.my_project.data_base_schema.renderer.dialects.MariaDbDialect
import gog.my_project.data_base_schema.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




class A1ExampleDropColumnV1Test {
    @Test
    fun rendersDropColumnForMySql() {
        val sql = Schema.render(A1ExampleDropColumnV1().migration(), SchemaDialect.MYSQL)

        assertEquals("ALTER TABLE `users` DROP COLUMN `display_name`", sql)
    }

    @Test
    fun ifExistsDefaultsToFalseAndCanBeEnabledInAst() {
        val ordinary = A1ExampleDropColumnV1().migration()
        val optional = A1ExampleDropColumnV1(useIfExists = true).migration()

        assertEquals(
            "ALTER TABLE `users` DROP COLUMN `display_name`",
            Schema.render(ordinary, SchemaDialect.MYSQL),
        )
        assertFailsWith<UnsupportedOperationException> { Schema.render(optional, SchemaDialect.MYSQL) }
        assertEquals(
            "ALTER TABLE `users` DROP COLUMN IF EXISTS `display_name`",
            Schema.render(optional, SchemaDialect.MARIA_DB),
        )
    }

    @Test
    fun builderTrimsAndStoresIdentifiersInAst() {
        val migration = SchemaDropColumnBuilder().apply {
            tableName("  users  ")
            name("  email  ")
        }

        assertEquals("users", migration.ast.metadata.tableName)
        assertEquals("email", migration.ast.metadata.name)
        assertEquals("ALTER TABLE `users` DROP COLUMN `email`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rendersBlankTableNameForDatabaseValidation() {
        val migration = SchemaDropColumnBuilder().apply { tableName("   "); name("email") }
        assertEquals("ALTER TABLE `` DROP COLUMN `email`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rendersBlankColumnNameForDatabaseValidation() {
        val migration = SchemaDropColumnBuilder().apply { tableName("users"); name("   ") }
        assertEquals("ALTER TABLE `users` DROP COLUMN ``", MySqlDialect().render(migration.ast))
    }

    @Test
    fun defersMissingTableNameToRenderer() {
        val migration = SchemaDropColumnBuilder().apply { name("email") }
        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun defersMissingColumnNameToRenderer() {
        val migration = SchemaDropColumnBuilder().apply { tableName("users") }
        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun rendererRejectsIncompleteAstBeforeRendering() {
        val ast = SchemaDropColumnAst().apply {
            metadata.tableName = "users"
        }

        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(ast)
        }
    }

    @Test
    fun escapesTableAndColumnIdentifiers() {
        val migration = SchemaDropColumnBuilder().apply {
            tableName("user`data")
            name("display`name")
        }

        assertEquals(
            "ALTER TABLE `user``data` DROP COLUMN `display``name`",
            MySqlDialect().render(migration.ast),
        )
    }

}
