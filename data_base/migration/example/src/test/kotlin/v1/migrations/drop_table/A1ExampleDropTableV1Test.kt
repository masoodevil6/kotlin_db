package gog.my_project.data_base.migration.example.v1.migrations.drop_table

import gog.my_project.data_base_schema.SchemaDialect

import gog.my_project.data_base_schema.Schema


import gog.my_project.data_base_schema.builder.ast.drop_table.SchemaDropTableBuilder
import kotlin.test.assertNull
import gog.my_project.data_base_schema.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




class A1ExampleDropTableV1Test {

    @Test
    fun rendersDropTableWithoutIfExists() {
        val sql = Schema.render(
            A1ExampleDropTableV1(useIfExists = false).migration(),
            SchemaDialect.MYSQL,
        )

        assertEquals("DROP TABLE `users`", sql)
    }

    @Test
    fun rendersDropTableWithIfExists() {
        val sql = Schema.render(
            A1ExampleDropTableV1(useIfExists = true).migration(),
            SchemaDialect.MYSQL,
        )

        assertEquals("DROP TABLE IF EXISTS `users`", sql)
    }

    @Test
    fun trimsTableNameBeforeRendering() {
        val migration = SchemaDropTableBuilder().apply { tableName("  users  ") }

        assertEquals("DROP TABLE `users`", MySqlDialect().render(migration.ast))
    }

    @Test
    fun rendersBlankTableNameForDatabaseValidation() {
        val migration = SchemaDropTableBuilder().apply { tableName("   ") }
        assertEquals("DROP TABLE ``", MySqlDialect().render(migration.ast))
    }

    @Test
    fun ifExistsDefaultsToAbsentInAst() {
        val migration = SchemaDropTableBuilder().apply { tableName("users") }

        assertNull(migration.ast.ifExistAst)
    }
}
