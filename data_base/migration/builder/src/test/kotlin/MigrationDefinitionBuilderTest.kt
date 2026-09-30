package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrationDefinitionBuilderTest {
    @Test
    fun definitionCollectsMultipleOperationsInDeclarationOrder() {
        val definition = migration {
            dropTable { tableName("first") }
            createTable { table { tableName("middle") } }
            dropTable { tableName("last") }
        }

        assertEquals(3, definition.operations.size)
        val first = assertIs<IMigrationDropTableApi>(definition.operations[0])
        val middle = assertIs<IMigrationRenderCreateTableApi>(definition.operations[1])
        val last = assertIs<IMigrationDropTableApi>(definition.operations[2])
        assertEquals("first", first.ast.tableName)
        assertEquals("middle", middle.ast.migrationTableAst?.tableName)
        assertEquals("last", last.ast.tableName)
    }

    @Test
    fun repeatedOperationsArePreserved() {
        val definition = migration {
            dropTable { tableName("users") }
            dropTable { tableName("users_archive") }
        }

        assertEquals(2, definition.operations.size)
        assertEquals(
            listOf("users", "users_archive"),
            definition.operations.map { assertIs<IMigrationDropTableApi>(it).ast.tableName },
        )
    }

    @Test
    fun emptyDefinitionIsAllowed() {
        assertTrue(migration { }.operations.isEmpty())
    }

    @Test
    fun buildersRetainOperationSpecificConfiguration() {
        val definition = migration {
            dropColumn {
                tableName("users")
                name("legacy")
                ifExists()
            }
            renameColumn {
                tableName("users")
                name("display_name")
                to("full_name")
            }
        }

        val dropColumn = assertIs<IMigrationDropColumnApi>(definition.operations[0])
        val renameColumn = assertIs<IMigrationRenameColumnApi>(definition.operations[1])
        assertTrue(dropColumn.ast.ifExists)
        assertEquals("full_name", renameColumn.ast.to)
    }
}
