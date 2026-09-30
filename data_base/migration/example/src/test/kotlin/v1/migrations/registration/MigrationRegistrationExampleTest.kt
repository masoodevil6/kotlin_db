package gog.my_project.data_base.migration.example.v1.migrations.registration

import gog.my_project.data_base.migration.api.interfaces.MigrationGroup
import gog.my_project.data_base.migration.api.interfaces.SingleMigration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MigrationRegistrationExampleTest {
    @Test
    fun declaresIdentityAndRegistrationOrderWithoutExecutingMigrations() {
        val configuration = customerMigrationConfiguration()

        assertEquals(2, configuration.registrations.size)
        val create = assertIs<SingleMigration>(configuration.registrations[0]).migration
        assertEquals("create_customers", create.id.value)
        assertEquals(CreateCustomersRegistrationExample::class, create.migrationClass)

        val group = assertIs<MigrationGroup>(configuration.registrations[1])
        assertEquals("customers", group.name)
        assertEquals(listOf("add_customer_email"), group.migrations.map { it.id.value })
        assertEquals(AddCustomerEmailRegistrationExample::class, group.migrations.single().migrationClass)
    }
}
