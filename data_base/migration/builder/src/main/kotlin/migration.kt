package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi
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
class MigrationDefinitionBuilder {
    private val operations = mutableListOf<IMigrationApi<*>>()

    fun createTable(block: IMigrationRenderCreateTableApi.() -> Unit) {
        val builder = MigrationRenderCreateTableBuilder()
        builder.block()
        append(builder)
    }

    fun dropTable(block: IMigrationDropTableApi.() -> Unit) {
        val builder = MigrationDropTableBuilder()
        builder.block()
        append(builder)
    }

    fun renameTable(block: IMigrationRenameTableApi.() -> Unit) {
        val builder = MigrationRenameTableBuilder()
        builder.block()
        append(builder)
    }

    fun addColumn(block: IMigrationAddColumnApi.() -> Unit) {
        val builder = MigrationAddColumnBuilder()
        builder.block()
        append(builder)
    }

    fun dropColumn(block: IMigrationDropColumnApi.() -> Unit) {
        val builder = MigrationDropColumnBuilder()
        builder.block()
        append(builder)
    }

    fun renameColumn(block: IMigrationRenameColumnApi.() -> Unit) {
        val builder = MigrationRenameColumnBuilder()
        builder.block()
        append(builder)
    }

    fun modifyColumn(block: IMigrationModifyColumnApi.() -> Unit) {
        val builder = MigrationModifyColumnBuilder()
        builder.block()
        append(builder)
    }

    fun createIndex(block: IMigrationCreateIndexApi.() -> Unit) {
        val builder = MigrationCreateIndexBuilder()
        builder.block()
        append(builder)
    }

    fun dropIndex(block: IMigrationDropIndexApi.() -> Unit) {
        val builder = MigrationDropIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createUniqueIndex(block: IMigrationCreateUniqueIndexApi.() -> Unit) {
        val builder = MigrationCreateUniqueIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createMultiColumnIndex(block: IMigrationCreateMultiColumnIndexApi.() -> Unit) {
        val builder = MigrationCreateMultiColumnIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createFullTextIndex(block: IMigrationCreateFullTextIndexApi.() -> Unit) {
        val builder = MigrationCreateFullTextIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createSpatialIndex(block: IMigrationCreateSpatialIndexApi.() -> Unit) {
        val builder = MigrationCreateSpatialIndexBuilder()
        builder.block()
        append(builder)
    }

    fun createForeignKey(block: IMigrationCreateForeignKeyApi.() -> Unit) {
        val builder = MigrationCreateForeignKeyBuilder()
        builder.block()
        append(builder)
    }

    fun dropForeignKey(block: IMigrationDropForeignKeyApi.() -> Unit) {
        val builder = MigrationDropForeignKeyBuilder()
        builder.block()
        append(builder)
    }

    internal fun build(): MigrationDefinition = MigrationDefinition(operations)

    private fun append(operation: IMigrationApi<*>) {
        operations.add(operation)
    }
}

fun migration(block: MigrationDefinitionBuilder.() -> Unit): MigrationDefinition =
    MigrationDefinitionBuilder().apply(block).build()
