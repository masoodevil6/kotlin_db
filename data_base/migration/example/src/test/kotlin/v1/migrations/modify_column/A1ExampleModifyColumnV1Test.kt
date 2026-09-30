package gog.my_project.data_base.migration.example.v1.migrations.modify_column

import gog.my_project.data_base.migration.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base.migration.builder.modifyColumn
import gog.my_project.data_base.migration.params.data_types.BooleanType
import gog.my_project.data_base.migration.params.data_types.DecimalType
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.JsonType
import gog.my_project.data_base.migration.params.data_types.TextType
import gog.my_project.data_base.migration.params.data_types.VarcharType
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class A1ExampleModifyColumnV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersFullDesiredDefinition() {
        assertEquals(
            "ALTER TABLE `users` MODIFY COLUMN `name` VARCHAR(255) NOT NULL DEFAULT 'Unknown'",
            dialect.render(A1ExampleModifyColumnV1().migration().ast),
        )
    }

    @Test
    fun distinguishesNoDefaultFromDefaultNull() {
        val noDefault = modifyColumn {
            tableName("users"); name("value"); dataType(IntType()); nullable(); autoIncrement(false)
        }
        val explicitNull = modifyColumn {
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
        assertEquals(DefaultDefinition.NoDefaultClause, noDefault.ast.defaultDefinition)
        assertEquals(DefaultDefinition.DefaultNull, explicitNull.ast.defaultDefinition)
    }

    @Test
    fun rendersDefaultLiteralSafely() {
        val migration = modifyColumn {
            tableName("users"); name("value"); dataType(VarcharType(32)); notNull()
            default("O'Reilly"); autoIncrement(false)
        }
        assertTrue(dialect.render(migration.ast)!!.endsWith("NOT NULL DEFAULT 'O''Reilly'"))
    }

    @Test
    fun rejectsUnsupportedDefaultAndMissingRequiredStates() {
        val unsupported = modifyColumn {
            tableName("users"); name("value"); dataType(IntType()); notNull()
            default(Any()); autoIncrement(false)
        }
        assertFailsWith<IllegalArgumentException> { dialect.render(unsupported.ast) }
        assertFailsWith<IllegalArgumentException> {
            modifyColumn { tableName("users"); name("value"); dataType(IntType()) }.ast
        }
    }

    @Test
    fun escapesIdentifiersAndDoesNotRenderPrimaryKey() {
        val migration = modifyColumn {
            tableName("order"); name("user`data"); dataType(IntType()); notNull()
            autoIncrement(false)
        }
        val sql = dialect.render(migration.ast)!!
        assertTrue(sql.startsWith("ALTER TABLE `order` MODIFY COLUMN `user``data`"))
        assertFalse(sql.contains("PRIMARY KEY"))
    }

    @Test
    fun trimsIdentifiersAndRejectsBlankNames() {
        val trimmed = modifyColumn {
            tableName("  users  "); name("  display_name  "); dataType(VarcharType(20)); nullable()
            autoIncrement(false)
        }
        assertEquals(
            "ALTER TABLE `users` MODIFY COLUMN `display_name` VARCHAR(20) NULL",
            dialect.render(trimmed.ast),
        )
        assertFailsWith<IllegalArgumentException> {
            modifyColumn { tableName("   "); name("x"); dataType(IntType()); notNull(); autoIncrement(false) }.ast
        }
        assertFailsWith<IllegalArgumentException> {
            modifyColumn { tableName("users"); name("  "); dataType(IntType()); notNull(); autoIncrement(false) }.ast
        }
    }

    @Test
    fun rendersExplicitAutoIncrementTrueAndFalse() {
        val enabled = modifyColumn {
            tableName("users"); name("id"); dataType(IntType()); notNull(); autoIncrement(true)
        }
        val disabled = modifyColumn {
            tableName("users"); name("id"); dataType(IntType()); notNull(); autoIncrement(false)
        }
        assertTrue(dialect.render(enabled.ast)!!.endsWith("NOT NULL AUTO_INCREMENT"))
        assertFalse(dialect.render(disabled.ast)!!.contains("AUTO_INCREMENT"))
    }

    @Test
    fun setterCallOrderDoesNotChangeSql() {
        val first = modifyColumn {
            default(0); autoIncrement(false); notNull(); dataType(IntType()); name("id"); tableName("users")
        }
        val second = modifyColumn {
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
            val migration = modifyColumn {
                tableName("users"); name("value"); dataType(type); nullable(); autoIncrement(false)
            }
            assertTrue(dialect.render(migration.ast)!!.contains("`value` $expected NULL"))
        }
    }
}
