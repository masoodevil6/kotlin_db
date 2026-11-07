package gog.my_project.data_base.migration.example.v1.migrations.add_column

import gog.my_project.data_base_schema.SchemaDialect

import gog.my_project.data_base_schema.Schema


import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.params.data_types.IntType
import gog.my_project.data_base_schema.params.data_types.VarcharType
import gog.my_project.data_base_schema.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




class A1ExampleAddColumnV1Test {
    @Test
    fun rendersAddColumn() {
        val sql = Schema.render(A1ExampleAddColumnV1().migration(), SchemaDialect.MYSQL)

        assertEquals(
            "ALTER TABLE `users` ADD COLUMN `email` VARCHAR(255) NOT NULL",
            sql,
        )
    }

    @Test
    fun reusesColumnModifiersCorrectly() {
        val migration = SchemaAddColumnBuilder().apply {
            tableName("users")
            name("id")
            dataType(IntType())
            notNull()
            autoIncrement()
            primaryKey()
        }

        assertEquals(
            "ALTER TABLE `users` ADD COLUMN `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun reusesExistingDefaultRendering() {
        val migration = SchemaAddColumnBuilder().apply {
            tableName("users")
            name("display_name")
            dataType(VarcharType(120))
            notNull()
            default("O'Reilly")
        }

        assertEquals(
            "ALTER TABLE `users` ADD COLUMN `display_name` VARCHAR(120) NOT NULL DEFAULT 'O''Reilly'",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun escapesTableAndColumnIdentifiers() {
        val migration = SchemaAddColumnBuilder().apply {
            tableName("user`data")
            name("display`name")
            dataType(VarcharType(255))
            notNull()
        }

        assertEquals(
            "ALTER TABLE `user``data` ADD COLUMN `display``name` VARCHAR(255) NOT NULL",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun rejectsMissingColumn() {
        val migration = SchemaAddColumnBuilder().apply { tableName("users") }

        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun rendersAutoIncrementWithoutRevalidatingDatabaseKeyRules() {
        val migration = SchemaAddColumnBuilder().apply {
            tableName("users")
            name("id")
            dataType(IntType())
            autoIncrement()
        }

        assertEquals(
            "ALTER TABLE `users` ADD COLUMN `id` INT NOT NULL AUTO_INCREMENT",
            MySqlDialect().render(migration.ast),
        )
    }

}
