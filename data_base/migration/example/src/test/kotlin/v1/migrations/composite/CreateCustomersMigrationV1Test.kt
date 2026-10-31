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
    fun tableAndIndexAreSeparateSingleOperationMigrations() {
        val createTableMigration = CreateCustomersMigrationV1()
        val createIndexMigration = CreateCustomersEmailIndexMigrationV1()
        val tableUp = createTableMigration.up()
        val tableDown = createTableMigration.down()
        val indexUp = createIndexMigration.up()
        val indexDown = createIndexMigration.down()

        assertEquals(1, tableUp.operations.size)
        assertEquals(1, tableDown.operations.size)
        assertEquals(1, indexUp.operations.size)
        assertEquals(1, indexDown.operations.size)

        val createTable = assertIs<IMigrationRenderCreateTableApi>(tableUp.operations.single())
        assertIs<IMigrationCreateIndexApi>(indexUp.operations.single())
        assertEquals("customers", createTable.ast.migrationTableAst?.metadata?.tableName)

        val forwardOperations = listOf(tableUp.operations.single(), indexUp.operations.single())
        val rollbackOperations = listOf(indexDown.operations.single(), tableDown.operations.single())
        assertIs<IMigrationRenderCreateTableApi>(forwardOperations[0])
        assertIs<IMigrationCreateIndexApi>(forwardOperations[1])
        assertIs<IMigrationDropIndexApi>(rollbackOperations[0])
        assertIs<IMigrationDropTableApi>(rollbackOperations[1])
    }
}
