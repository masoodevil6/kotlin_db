package gog.my_project.data_base.migration.example.v1.migrations.add_column

import gog.my_project.data_base.migration.builder.addColumn
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.VarcharType
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class A1ExampleAddColumnV1Test {
    @Test
    fun rendersAddColumn() {
        val sql = MySqlDialect().render(A1ExampleAddColumnV1().migration().ast)

        assertEquals(
            "ALTER TABLE `users` ADD COLUMN `email` VARCHAR(255) NOT NULL",
            sql,
        )
    }

    @Test
    fun reusesColumnModifiersCorrectly() {
        val migration = addColumn {
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
        val migration = addColumn {
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
        val migration = addColumn {
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
        val migration = addColumn { tableName("users") }

        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun rejectsAutoIncrementWithoutPrimaryKey() {
        val migration = addColumn {
            tableName("users")
            name("id")
            dataType(IntType())
            autoIncrement()
        }

        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun keepsIdentifierParametersEmpty() {
        assertEquals(0, A1ExampleAddColumnV1().migration().params.size)
    }
}