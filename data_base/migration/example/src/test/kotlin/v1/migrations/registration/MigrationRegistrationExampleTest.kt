package gog.my_project.data_base.migration.example.v1.migrations.registration

import gog.my_project.data_base_migration.api.registration.group.MigrationGroup
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrationRegistrationExampleTest {
    @Test
    fun declaresIdentityAndRegistrationOrderWithoutExecutingMigrations() {
        val configuration = customerMigrationConfiguration()

        assertEquals(1, configuration.registrations.size)
        val version = assertIs<MigrationVersion>(configuration.registrations.single())
        assertEquals("customers_v1", version.name)
        val create = assertIs<SingleMigration>(version.registrations[0]).migration
        assertEquals("create_customers", create.id.value)
        assertEquals(CreateCustomersRegistrationExample::class, create.migrationClass)

        val group = assertIs<MigrationGroup>(version.registrations[1])
        assertEquals("customers", group.name)
        assertEquals(listOf("add_customer_email"), group.registrations.map { assertIs<SingleMigration>(it).migration.id.value })
        assertEquals(
            AddCustomerEmailRegistrationExample::class,
            assertIs<SingleMigration>(group.registrations.single()).migration.migrationClass,
        )
    }

    @Test
    fun registeredMigrationsHaveOneOperationAndReverseTheirDependencyOrder() {
        val createCustomers = CreateCustomersRegistrationExample()
        val addEmail = AddCustomerEmailRegistrationExample()

        assertTrue(
            Schema.render(createCustomers.up().operation, SchemaDialect.MYSQL)
                .contains("CREATE TABLE `customers`"),
        )
        assertEquals(
            "ALTER TABLE `customers` ADD COLUMN `email` VARCHAR(255) NOT NULL",
            Schema.render(addEmail.up().operation, SchemaDialect.MYSQL),
        )

        val rollbackDropColumn = Schema.render(addEmail.down().operation, SchemaDialect.MYSQL)
        val rollbackDropTable = Schema.render(createCustomers.down().operation, SchemaDialect.MYSQL)
        assertEquals("ALTER TABLE `customers` DROP COLUMN `email`", rollbackDropColumn)
        assertEquals("DROP TABLE `customers`", rollbackDropTable)
    }
}
