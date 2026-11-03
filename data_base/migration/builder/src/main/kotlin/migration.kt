package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi
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
class MigrationDefinitionBuilder {
    private val operations = mutableListOf<ISchemaApi<*>>()

    fun createTable(block: ISchemaRenderCreateTableApi.() -> Unit) {
        val builder = SchemaRenderCreateTableBuilder()
        builder.block()
        append(builder)
    }

    fun dropTable(block: ISchemaDropTableApi.() -> Unit) {
        val builder = SchemaDropTableBuilder()
        builder.block()
        append(builder)
    }

    fun renameTable(block: ISchemaRenameTableApi.() -> Unit) {
        val builder = SchemaRenameTableBuilder()
        builder.block()
        append(builder)
    }

    fun addColumn(block: ISchemaAddColumnApi.() -> Unit) {
        val builder = SchemaAddColumnBuilder()
        builder.block()
        append(builder)
    }

    fun dropColumn(block: ISchemaDropColumnApi.() -> Unit) {
        val builder = SchemaDropColumnBuilder()
        builder.block()
        append(builder)
    }

    fun renameColumn(block: ISchemaRenameColumnApi.() -> Unit) {
        val builder = SchemaRenameColumnBuilder()
        builder.block()
        append(builder)
    }

    fun modifyColumn(block: ISchemaModifyColumnApi.() -> Unit) {
        val builder = SchemaModifyColumnBuilder()
        builder.block()
        append(builder)
    }

    fun createIndex(block: ISchemaCreateIndexApi.() -> Unit) {
        val builder = SchemaCreateIndexBuilder()
        builder.block()
        append(builder)
    }

    fun dropIndex(block: ISchemaDropIndexApi.() -> Unit) {
        val builder = SchemaDropIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createUniqueIndex(block: ISchemaCreateUniqueIndexApi.() -> Unit) {
        val builder = SchemaCreateUniqueIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createMultiColumnIndex(block: ISchemaCreateMultiColumnIndexApi.() -> Unit) {
        val builder = SchemaCreateMultiColumnIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createFullTextIndex(block: ISchemaCreateFullTextIndexApi.() -> Unit) {
        val builder = SchemaCreateFullTextIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createSpatialIndex(block: ISchemaCreateSpatialIndexApi.() -> Unit) {
        val builder = SchemaCreateSpatialIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createForeignKey(block: ISchemaCreateForeignKeyApi.() -> Unit) {
        val builder = SchemaCreateForeignKeyBuilder()
        builder.block()
        append(builder)
    }

    fun dropForeignKey(block: ISchemaDropForeignKeyApi.() -> Unit) {
        val builder = SchemaDropForeignKeyBuilder()
        builder.block()
        append(builder)
    }

    internal fun build(): MigrationDefinition = MigrationDefinition(operations)

    private fun append(operation: ISchemaApi<*>) {
        operations.add(operation)
    }
}

fun migration(block: MigrationDefinitionBuilder.() -> Unit): MigrationDefinition =
    MigrationDefinitionBuilder().apply(block).build()
