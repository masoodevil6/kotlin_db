package gog.my_project.data_base.migration.example.v1.migrations.composite

import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreateCustomersMigrationV1Test {
    @Test
    fun upAndDownReturnOrderedDefinitionsWithoutRunningThem() {
        val migration = CreateCustomersMigrationV1()

        val up = migration.up()
        val down = migration.down()

        assertEquals(2, up.operations.size)
        val createTable = assertIs<IMigrationRenderCreateTableApi>(up.operations[0])
        assertIs<IMigrationCreateIndexApi>(up.operations[1])
        assertEquals("customers", createTable.ast.migrationTableAst?.tableName)

        assertEquals(2, down.operations.size)
        assertIs<IMigrationDropIndexApi>(down.operations[0])
        assertIs<IMigrationDropTableApi>(down.operations[1])
    }
}
