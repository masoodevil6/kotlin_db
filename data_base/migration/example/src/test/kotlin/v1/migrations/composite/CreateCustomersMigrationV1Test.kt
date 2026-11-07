package gog.my_project.data_base.migration.example.v1.migrations.composite

import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateCustomersMigrationV1Test {
    @Test
    fun tableAndIndexAreSeparateSingleOperationMigrations() {
        val createTableMigration = CreateCustomersMigrationV1()
        val createIndexMigration = CreateCustomersEmailIndexMigrationV1()
        val tableUp = createTableMigration.up()
        val tableDown = createTableMigration.down()
        val indexUp = createIndexMigration.up()
        val indexDown = createIndexMigration.down()

        val createTableSql = Schema.render(tableUp.operation, SchemaDialect.MYSQL)
        val createIndexSql = Schema.render(indexUp.operation, SchemaDialect.MYSQL)
        assertTrue(createTableSql.contains("CREATE TABLE `customers`"))
        assertTrue(createIndexSql.contains("CREATE INDEX `idx_customers_email`"))

        val rollbackIndexSql = Schema.render(indexDown.operation, SchemaDialect.MYSQL)
        val rollbackTableSql = Schema.render(tableDown.operation, SchemaDialect.MYSQL)
        assertTrue(rollbackIndexSql.contains("DROP INDEX `idx_customers_email`"))
        assertTrue(rollbackTableSql.contains("DROP TABLE `customers`"))
    }
}
