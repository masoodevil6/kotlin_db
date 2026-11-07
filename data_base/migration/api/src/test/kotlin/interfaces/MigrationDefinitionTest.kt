package gog.my_project.data_base.migration.api.interfaces

import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_schema.Schema
import kotlin.test.Test
import kotlin.test.assertSame

class MigrationDefinitionTest {
    @Test
    fun storesTheSuppliedSingleOperation() {
        val operation = Schema.dropTable { tableName("first") }
        val definition = MigrationDefinition(operation)

        assertSame(operation, definition.operation)
    }
}
