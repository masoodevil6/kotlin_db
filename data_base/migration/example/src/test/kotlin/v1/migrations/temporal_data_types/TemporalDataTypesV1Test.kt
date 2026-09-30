package gog.my_project.data_base.migration.example.v1.migrations.temporal_data_types

import gog.my_project.data_base.migration.builder.addColumn
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.builder.modifyColumn
import gog.my_project.data_base.migration.params.data_types.DateTimeType
import gog.my_project.data_base.migration.params.data_types.DateType
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.TimeType
import gog.my_project.data_base.migration.params.data_types.TimestampType
import gog.my_project.data_base.migration.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemporalDataTypesV1Test {
    private val mysql = MySqlDialect()
    private val mariaDb = MariaDbDialect()

    @Test
    fun createTableRendersTemporalTypesAndFractionalPrecision() {
        val sql = mysql.render(A1ExampleTemporalDataTypesV1().migration().ast)!!

        assertTrue(sql.contains("`event_date` DATE NOT NULL"))
        assertTrue(sql.contains("`elapsed_time` TIME(3) NOT NULL"))
        assertTrue(sql.contains("`local_event_time` DATETIME NOT NULL"))
        assertTrue(sql.contains("`recorded_at` TIMESTAMP(6) NOT NULL"))
    }

    @Test
    fun defaultAndExplicitZeroPrecisionRenderAsSpecified() {
        val sql = mysql.render(
            createTable {
                table { tableName("temporal_values") }
                addColumn { name("date_value"); dataType(DateType()) }
                addColumn { name("time_default"); dataType(TimeType()) }
                addColumn { name("time_zero"); dataType(TimeType(0)) }
                addColumn { name("datetime_zero"); dataType(DateTimeType(0)) }
                addColumn { name("timestamp_zero"); dataType(TimestampType(0)) }
            }.ast,
        )!!

        assertTrue(sql.contains("`time_default` TIME NOT NULL"))
        assertTrue(sql.contains("`time_zero` TIME(0) NOT NULL"))
        assertTrue(sql.contains("`datetime_zero` DATETIME(0) NOT NULL"))
        assertTrue(sql.contains("`timestamp_zero` TIMESTAMP(0) NOT NULL"))
    }

    @Test
    fun addColumnRendersTemporalTypeAndEscapesLiteralDefault() {
        val sql = mysql.render(
            addColumn {
                tableName("events")
                name("created_at")
                dataType(TimestampType(6))
                notNull()
                default("2026-09-29 12:13:14.123456")
            }.ast,
        )

        assertEquals(
            "ALTER TABLE `events` ADD COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT '2026-09-29 12:13:14.123456'",
            sql,
        )
    }

    @Test
    fun modifyColumnKeepsTypeNullabilityAndDefaultIndependent() {
        val noDefault = modifyColumn {
            tableName("events"); name("updated_at"); dataType(DateTimeType(2)); nullable(); autoIncrement(false)
        }
        val stringDefault = modifyColumn {
            tableName("events"); name("started_at"); dataType(TimestampType()); notNull()
            default("2026-09-29 12:13:14"); autoIncrement(false)
        }

        assertEquals(
            "ALTER TABLE `events` MODIFY COLUMN `updated_at` DATETIME(2) NULL",
            mysql.render(noDefault.ast),
        )
        assertEquals(
            "ALTER TABLE `events` MODIFY COLUMN `started_at` TIMESTAMP NOT NULL DEFAULT '2026-09-29 12:13:14'",
            mysql.render(stringDefault.ast),
        )
        assertFalse(mysql.render(noDefault.ast)!!.contains("DEFAULT"))
    }

    @Test
    fun rejectsPrecisionOutsideSupportedRangeAtTypeConstruction() {
        assertFailsWith<IllegalArgumentException> { TimeType(-1) }
        assertFailsWith<IllegalArgumentException> { TimeType(7) }
        assertFailsWith<IllegalArgumentException> { DateTimeType(-1) }
        assertFailsWith<IllegalArgumentException> { DateTimeType(7) }
        assertFailsWith<IllegalArgumentException> { TimestampType(-1) }
        assertFailsWith<IllegalArgumentException> { TimestampType(7) }
    }

    @Test
    fun rejectsNonStringTemporalDefaultsInCreateAddAndModify() {
        val create = createTable {
            table { tableName("events") }
            addColumn { name("created_at"); dataType(TimestampType()); default(123) }
        }
        val add = addColumn {
            tableName("events"); name("created_at"); dataType(TimestampType()); default(123)
        }
        val modify = modifyColumn {
            tableName("events"); name("created_at"); dataType(TimestampType()); nullable()
            default(123); autoIncrement(false)
        }

        assertFailsWith<IllegalArgumentException> { mysql.render(create.ast) }
        assertFailsWith<IllegalArgumentException> { mysql.render(add.ast) }
        assertFailsWith<IllegalArgumentException> { mysql.render(modify.ast) }
    }

    @Test
    fun mariadbDialectUsesItsRegisteredTemporalRendering() {
        val sql = mariaDb.render(
            createTable {
                table { tableName("temporal_values") }
                addColumn { name("happened_at"); dataType(TimestampType(4)); nullable() }
            }.ast,
        )

        assertEquals(
            "CREATE TABLE `temporal_values` (\n  `happened_at` TIMESTAMP(4) NULL\n)",
            sql,
        )
    }

    @Test
    fun temporalTypeDefaultsDoNotBroadenExistingNumericDefaults() {
        val existingType = createTable {
            table { tableName("temporal_values") }
            addColumn { name("count"); dataType(IntType()); default(5) }
        }

        assertTrue(mysql.render(existingType.ast)!!.contains("`count` INT NOT NULL DEFAULT 5"))
    }
}
