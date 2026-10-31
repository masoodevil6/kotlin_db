package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.builder.ast.add_column.MigrationAddColumnBuilder
import gog.my_project.data_base.migration.builder.ast.create_foreign_key.MigrationCreateForeignKeyBuilder
import gog.my_project.data_base.migration.builder.ast.create_full_text_index.MigrationCreateFullTextIndexBuilder
import gog.my_project.data_base.migration.builder.ast.create_index.MigrationCreateIndexBuilder
import gog.my_project.data_base.migration.builder.ast.create_multi_column_index.MigrationCreateMultiColumnIndexBuilder
import gog.my_project.data_base.migration.builder.ast.create_spatial_index.MigrationCreateSpatialIndexBuilder
import gog.my_project.data_base.migration.builder.ast.create_table.render_migration_create_table.MigrationRenderCreateTableBuilder
import gog.my_project.data_base.migration.builder.ast.create_unique_index.MigrationCreateUniqueIndexBuilder
import gog.my_project.data_base.migration.builder.ast.drop_column.MigrationDropColumnBuilder
import gog.my_project.data_base.migration.builder.ast.drop_foreign_key.MigrationDropForeignKeyBuilder
import gog.my_project.data_base.migration.builder.ast.drop_index.MigrationDropIndexBuilder
import gog.my_project.data_base.migration.builder.ast.drop_table.MigrationDropTableBuilder
import gog.my_project.data_base.migration.builder.ast.modify_column.MigrationModifyColumnBuilder
import gog.my_project.data_base.migration.builder.ast.rename_column.MigrationRenameColumnBuilder
import gog.my_project.data_base.migration.builder.ast.rename_table.MigrationRenameTableBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrationDefinitionBuilderTest {
    @Test
    fun oneTopLevelOperationIsAccepted() {
        val definition = migration { dropTable { tableName("only") } }

        assertEquals(1, definition.operations.size)
        val operation = assertIs<IMigrationDropTableApi>(definition.operations.single())
        assertEquals("only", operation.ast.metadata.tableName)
    }

    @Test
    fun emptyDefinitionIsRejected() {
        assertFailsWith<IllegalArgumentException> { migration { } }
    }

    @Test
    fun multipleTopLevelOperationsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            migration {
                dropTable { tableName("users") }
                dropTable { tableName("users_archive") }
            }
        }
    }

    @Test
    fun buildersRetainOperationSpecificConfiguration() {
        val dropDefinition = migration {
            dropColumn {
                tableName("users")
                name("legacy")
                ifExists()
            }
        }
        val renameDefinition = migration {
            renameColumn {
                tableName("users")
                name("display_name")
                to("full_name")
            }
        }

        val dropColumn = assertIs<IMigrationDropColumnApi>(dropDefinition.operations.single())
        val renameColumn = assertIs<IMigrationRenameColumnApi>(renameDefinition.operations.single())
        assertTrue(dropColumn.ast.metadata.ifExists)
        assertEquals("full_name", renameColumn.ast.metadata.to)
    }

    @Test
    fun everyOperationUsesItsConcreteBuilderWithoutSemanticValidation() {
        val definitions = listOf(
            migration { createTable { } },
            migration { dropTable { } },
            migration { renameTable { } },
            migration { addColumn { } },
            migration { dropColumn { } },
            migration { renameColumn { } },
            migration { modifyColumn { } },
            migration { createIndex { } },
            migration { dropIndex { } },
            migration { createUniqueIndex { } },
            migration { createMultiColumnIndex { } },
            migration { createFullTextIndex { } },
            migration { createSpatialIndex { } },
            migration { createForeignKey { } },
            migration { dropForeignKey { } },
        )

        val concreteBuilders = listOf(
            MigrationRenderCreateTableBuilder(),
            MigrationDropTableBuilder(),
            MigrationRenameTableBuilder(),
            MigrationAddColumnBuilder(),
            MigrationDropColumnBuilder(),
            MigrationRenameColumnBuilder(),
            MigrationModifyColumnBuilder(),
            MigrationCreateIndexBuilder(),
            MigrationDropIndexBuilder(),
            MigrationCreateUniqueIndexBuilder(),
            MigrationCreateMultiColumnIndexBuilder(),
            MigrationCreateFullTextIndexBuilder(),
            MigrationCreateSpatialIndexBuilder(),
            MigrationCreateForeignKeyBuilder(),
            MigrationDropForeignKeyBuilder(),
        )

        val operations = definitions.map { it.operations.single() }
        assertEquals(15, operations.size)
        assertEquals(
            concreteBuilders.map { it::class },
            operations.map { it::class },
        )
        assertEquals(
            concreteBuilders.map { it.ast::class },
            operations.map { it.ast::class },
        )
    }

    @Test
    fun nestedCreateTableDefinitionsRemainOneTopLevelOperation() {
        val definition = migration {
            createTable {
                table { tableName("customers") }
                addColumn { name("id") }
                addColumn { name("email") }
            }
        }

        val createTable = assertIs<IMigrationRenderCreateTableApi>(definition.operations.single())
        assertEquals("customers", createTable.ast.migrationTableAst?.metadata?.tableName)
        assertEquals(2, createTable.ast.columns.size)
    }
}
