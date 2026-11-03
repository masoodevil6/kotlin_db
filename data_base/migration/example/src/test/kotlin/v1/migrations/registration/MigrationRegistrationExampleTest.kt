package gog.my_project.data_base.migration.example.v1.migrations.registration

import gog.my_project.data_base.migration.api.interfaces.MigrationGroup
import gog.my_project.data_base.migration.api.interfaces.SingleMigration
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
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

    @Test
    fun registeredMigrationsHaveOneOperationAndReverseTheirDependencyOrder() {
        val createCustomers = CreateCustomersRegistrationExample()
        val addEmail = AddCustomerEmailRegistrationExample()

        assertIs<ISchemaRenderCreateTableApi>(createCustomers.up().operations.single())
        assertIs<ISchemaAddColumnApi>(addEmail.up().operations.single())

        val rollbackInReverseRegistrationOrder = listOf(
            addEmail.down().operations.single(),
            createCustomers.down().operations.single(),
        )
        assertIs<ISchemaDropColumnApi>(rollbackInReverseRegistrationOrder[0])
        assertIs<ISchemaDropTableApi>(rollbackInReverseRegistrationOrder[1])
    }
}
