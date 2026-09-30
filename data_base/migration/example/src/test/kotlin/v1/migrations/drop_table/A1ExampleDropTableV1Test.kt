package gog.my_project.data_base.migration.example.v1.migrations.drop_table

import gog.my_project.data_base.migration.builder.dropTable
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class A1ExampleDropTableV1Test {

    @Test
    fun rendersDropTableWithoutIfExists() {
        val sql = MySqlDialect().render(
            A1ExampleDropTableV1(useIfExists = false).migration().ast,
        )

        assertEquals("DROP TABLE `users`", sql)
    }

    @Test
    fun rendersDropTableWithIfExists() {
        val sql = MySqlDialect().render(
            A1ExampleDropTableV1(useIfExists = true).migration().ast,
        )

        assertEquals("DROP TABLE IF EXISTS `users`", sql)
    }

    @Test
    fun trimsTableNameBeforeRendering() {
        val migration = dropTable { tableName("  users  ") }

        assertEquals("DROP TABLE `users`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rejectsBlankTableName() {
        assertFailsWith<IllegalArgumentException> {
            dropTable { tableName("   ") }
        }
    }

    @Test
    fun ifExistsDefaultsToFalseInAst() {
        val migration = dropTable { tableName("users") }

        assertEquals(false, migration.ast.ifExists)
    }
}