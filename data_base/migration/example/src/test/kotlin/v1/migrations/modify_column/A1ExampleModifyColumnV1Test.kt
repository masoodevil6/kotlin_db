package gog.my_project.data_base.migration.example.v1.migrations.modify_column

import gog.my_project.data_base_schema.SchemaDialect

import gog.my_project.data_base_schema.Schema


import gog.my_project.data_base_schema.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base_schema.builder.ast.modify_column.SchemaModifyColumnBuilder
import gog.my_project.data_base_schema.params.data_types.BooleanType
import gog.my_project.data_base_schema.params.data_types.DecimalType
import gog.my_project.data_base_schema.params.data_types.IntType
import gog.my_project.data_base_schema.params.data_types.JsonType
import gog.my_project.data_base_schema.params.data_types.TextType
import gog.my_project.data_base_schema.params.data_types.VarcharType
import gog.my_project.data_base_schema.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test




class A1ExampleModifyColumnV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersFullDesiredDefinition() {
        assertEquals(
            "ALTER TABLE `users` MODIFY COLUMN `name` VARCHAR(255) NOT NULL DEFAULT 'Unknown'",
            Schema.render(A1ExampleModifyColumnV1().migration(), SchemaDialect.MYSQL),
        )
    }

    @Test
    fun distinguishesNoDefaultFromDefaultNull() {
        val noDefault = SchemaModifyColumnBuilder().apply {
            tableName("users"); name("value"); dataType(IntType()); nullable(); autoIncrement(false)
        }
        val explicitNull = SchemaModifyColumnBuilder().apply {
            tableName("users"); name("value"); dataType(IntType()); nullable(); default(null); autoIncrement(false)
        }
        assertEquals(
            "ALTER TABLE `users` MODIFY COLUMN `value` INT NULL",
            dialect.render(noDefault.ast),
        )
        assertEquals(
            "ALTER TABLE `users` MODIFY COLUMN `value` INT NULL DEFAULT NULL",
            dialect.render(explicitNull.ast),
        )
        assertEquals(DefaultDefinition.NoDefaultClause, noDefault.ast.metadata.defaultDefinition)
        assertEquals(DefaultDefinition.DefaultNull, explicitNull.ast.metadata.defaultDefinition)
    }

    @Test
    fun rendersDefaultLiteralSafely() {
        val migration = SchemaModifyColumnBuilder().apply {
            tableName("users"); name("value"); dataType(VarcharType(32)); notNull()
            default("O'Reilly"); autoIncrement(false)
        }
        assertTrue(dialect.render(migration.ast)!!.endsWith("NOT NULL DEFAULT 'O''Reilly'"))
    }

    @Test
    fun rejectsUnsupportedDefaultAndMissingRequiredStates() {
        val unsupported = SchemaModifyColumnBuilder().apply {
            tableName("users"); name("value"); dataType(IntType()); notNull()
            default(Any()); autoIncrement(false)
        }
        assertFailsWith<IllegalArgumentException> { dialect.render(unsupported.ast) }
        val missingNullability = SchemaModifyColumnBuilder().apply { tableName("users"); name("value"); dataType(IntType()) }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(missingNullability.ast)
        }
    }

    @Test
    fun escapesIdentifiersAndDoesNotRenderPrimaryKey() {
        val migration = SchemaModifyColumnBuilder().apply {
            tableName("order"); name("user`data"); dataType(IntType()); notNull()
            autoIncrement(false)
        }
        val sql = dialect.render(migration.ast)!!
        assertTrue(sql.startsWith("ALTER TABLE `order` MODIFY COLUMN `user``data`"))
        assertFalse(sql.contains("PRIMARY KEY"))
    }

    @Test
    fun trimsIdentifiersAndRendersBlankNamesForDatabaseValidation() {
        val trimmed = SchemaModifyColumnBuilder().apply {
            tableName("  users  "); name("  display_name  "); dataType(VarcharType(20)); nullable()
            autoIncrement(false)
        }
        assertEquals(
            "ALTER TABLE `users` MODIFY COLUMN `display_name` VARCHAR(20) NULL",
            dialect.render(trimmed.ast),
        )
        assertEquals(
            "ALTER TABLE `` MODIFY COLUMN `x` INT NOT NULL",
            dialect.render(SchemaModifyColumnBuilder().apply { tableName("   "); name("x"); dataType(IntType()); notNull(); autoIncrement(false) }.ast),
        )
        assertEquals(
            "ALTER TABLE `users` MODIFY COLUMN `` INT NOT NULL",
            dialect.render(SchemaModifyColumnBuilder().apply { tableName("users"); name("  "); dataType(IntType()); notNull(); autoIncrement(false) }.ast),
        )
    }

    @Test
    fun rendersExplicitAutoIncrementTrueAndFalse() {
        val enabled = SchemaModifyColumnBuilder().apply {
            tableName("users"); name("id"); dataType(IntType()); notNull(); autoIncrement(true)
        }
        val disabled = SchemaModifyColumnBuilder().apply {
            tableName("users"); name("id"); dataType(IntType()); notNull(); autoIncrement(false)
        }
        assertTrue(dialect.render(enabled.ast)!!.endsWith("NOT NULL AUTO_INCREMENT"))
        assertFalse(dialect.render(disabled.ast)!!.contains("AUTO_INCREMENT"))
    }

    @Test
    fun setterCallOrderDoesNotChangeSql() {
        val first = SchemaModifyColumnBuilder().apply {
            default(0); autoIncrement(false); notNull(); dataType(IntType()); name("id"); tableName("users")
        }
        val second = SchemaModifyColumnBuilder().apply {
            tableName("users"); name("id"); dataType(IntType()); notNull(); default(0); autoIncrement(false)
        }
        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }

    @Test
    fun rendersEveryTracedColumnType() {
        val types = listOf(
            VarcharType(12) to "VARCHAR(12)",
            TextType() to "TEXT",
            IntType() to "INT",
            BooleanType() to "TINYINT(1)",
            DecimalType(8, 2) to "DECIMAL(8, 2)",
            JsonType() to "JSON",
        )
        types.forEach { (type, expected) ->
            val migration = SchemaModifyColumnBuilder().apply {
                tableName("users"); name("value"); dataType(type); nullable(); autoIncrement(false)
            }
            assertTrue(dialect.render(migration.ast)!!.contains("`value` $expected NULL"))
        }
    }
}
