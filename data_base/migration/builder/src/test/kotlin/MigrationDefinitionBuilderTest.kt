package gog.my_project.data_base.migration.builder

import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.builder.ast.create_foreign_key.SchemaCreateForeignKeyBuilder
import gog.my_project.data_base_schema.builder.ast.create_full_text_index.SchemaCreateFullTextIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_multi_column_index.SchemaCreateMultiColumnIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_spatial_index.SchemaCreateSpatialIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table.SchemaRenderCreateTableBuilder
import gog.my_project.data_base_schema.builder.ast.create_unique_index.SchemaCreateUniqueIndexBuilder
import gog.my_project.data_base_schema.builder.ast.drop_column.SchemaDropColumnBuilder
import gog.my_project.data_base_schema.builder.ast.drop_foreign_key.SchemaDropForeignKeyBuilder
import gog.my_project.data_base_schema.builder.ast.drop_index.SchemaDropIndexBuilder
import gog.my_project.data_base_schema.builder.ast.drop_table.SchemaDropTableBuilder
import gog.my_project.data_base_schema.builder.ast.modify_column.SchemaModifyColumnBuilder
import gog.my_project.data_base_schema.builder.ast.rename_column.SchemaRenameColumnBuilder
import gog.my_project.data_base_schema.builder.ast.rename_table.SchemaRenameTableBuilder
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
        val operation = assertIs<ISchemaDropTableApi>(definition.operations.single())
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

        val dropColumn = assertIs<ISchemaDropColumnApi>(dropDefinition.operations.single())
        val renameColumn = assertIs<ISchemaRenameColumnApi>(renameDefinition.operations.single())
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
            SchemaRenderCreateTableBuilder(),
            SchemaDropTableBuilder(),
            SchemaRenameTableBuilder(),
            SchemaAddColumnBuilder(),
            SchemaDropColumnBuilder(),
            SchemaRenameColumnBuilder(),
            SchemaModifyColumnBuilder(),
            SchemaCreateIndexBuilder(),
            SchemaDropIndexBuilder(),
            SchemaCreateUniqueIndexBuilder(),
            SchemaCreateMultiColumnIndexBuilder(),
            SchemaCreateFullTextIndexBuilder(),
            SchemaCreateSpatialIndexBuilder(),
            SchemaCreateForeignKeyBuilder(),
            SchemaDropForeignKeyBuilder(),
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

        val createTable = assertIs<ISchemaRenderCreateTableApi>(definition.operations.single())
        assertEquals("customers", createTable.ast.schemaTableAst?.metadata?.tableName)
        assertEquals(2, createTable.ast.columns.size)
    }
}
