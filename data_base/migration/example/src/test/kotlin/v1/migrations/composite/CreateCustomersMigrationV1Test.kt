package gog.my_project.data_base.migration.example.v1.migrations.composite

import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
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

        val createTable = assertIs<ISchemaRenderCreateTableApi>(tableUp.operations.single())
        assertIs<ISchemaCreateIndexApi>(indexUp.operations.single())
        assertEquals("customers", createTable.ast.schemaTableAst?.metadata?.tableName)

        val forwardOperations = listOf(tableUp.operations.single(), indexUp.operations.single())
        val rollbackOperations = listOf(indexDown.operations.single(), tableDown.operations.single())
        assertIs<ISchemaRenderCreateTableApi>(forwardOperations[0])
        assertIs<ISchemaCreateIndexApi>(forwardOperations[1])
        assertIs<ISchemaDropIndexApi>(rollbackOperations[0])
        assertIs<ISchemaDropTableApi>(rollbackOperations[1])
    }
}
