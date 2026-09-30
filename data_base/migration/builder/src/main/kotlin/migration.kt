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
import gog.my_project.data_base.migration.builder.addColumn as buildAddColumn
import gog.my_project.data_base.migration.builder.createForeignKey as buildCreateForeignKey
import gog.my_project.data_base.migration.builder.createFullTextIndex as buildCreateFullTextIndex
import gog.my_project.data_base.migration.builder.createIndex as buildCreateIndex
import gog.my_project.data_base.migration.builder.createMultiColumnIndex as buildCreateMultiColumnIndex
import gog.my_project.data_base.migration.builder.createSpatialIndex as buildCreateSpatialIndex
import gog.my_project.data_base.migration.builder.createTable as buildCreateTable
import gog.my_project.data_base.migration.builder.createUniqueIndex as buildCreateUniqueIndex
import gog.my_project.data_base.migration.builder.dropColumn as buildDropColumn
import gog.my_project.data_base.migration.builder.dropForeignKey as buildDropForeignKey
import gog.my_project.data_base.migration.builder.dropIndex as buildDropIndex
import gog.my_project.data_base.migration.builder.dropTable as buildDropTable
import gog.my_project.data_base.migration.builder.modifyColumn as buildModifyColumn
import gog.my_project.data_base.migration.builder.renameColumn as buildRenameColumn
import gog.my_project.data_base.migration.builder.renameTable as buildRenameTable

class MigrationDefinitionBuilder {
    private val operations = mutableListOf<IMigrationApi<*>>()

    fun createTable(block: IMigrationRenderCreateTableApi.() -> Unit) {
        append(buildCreateTable(block))
    }

    fun dropTable(block: IMigrationDropTableApi.() -> Unit) {
        append(buildDropTable(block))
    }

    fun renameTable(block: IMigrationRenameTableApi.() -> Unit) {
        append(buildRenameTable(block))
    }

    fun addColumn(block: IMigrationAddColumnApi.() -> Unit) {
        append(buildAddColumn(block))
    }

    fun dropColumn(block: IMigrationDropColumnApi.() -> Unit) {
        append(buildDropColumn(block))
    }

    fun renameColumn(block: IMigrationRenameColumnApi.() -> Unit) {
        append(buildRenameColumn(block))
    }

    fun modifyColumn(block: IMigrationModifyColumnApi.() -> Unit) {
        append(buildModifyColumn(block))
    }

    fun createIndex(block: IMigrationCreateIndexApi.() -> Unit) {
        append(buildCreateIndex(block))
    }

    fun dropIndex(block: IMigrationDropIndexApi.() -> Unit) {
        append(buildDropIndex(block))
    }

    fun createUniqueIndex(block: IMigrationCreateUniqueIndexApi.() -> Unit) {
        append(buildCreateUniqueIndex(block))
    }

    fun createMultiColumnIndex(block: IMigrationCreateMultiColumnIndexApi.() -> Unit) {
        append(buildCreateMultiColumnIndex(block))
    }

    fun createFullTextIndex(block: IMigrationCreateFullTextIndexApi.() -> Unit) {
        append(buildCreateFullTextIndex(block))
    }

    fun createSpatialIndex(block: IMigrationCreateSpatialIndexApi.() -> Unit) {
        append(buildCreateSpatialIndex(block))
    }

    fun createForeignKey(block: IMigrationCreateForeignKeyApi.() -> Unit) {
        append(buildCreateForeignKey(block))
    }

    fun dropForeignKey(block: IMigrationDropForeignKeyApi.() -> Unit) {
        append(buildDropForeignKey(block))
    }

    internal fun build(): MigrationDefinition = MigrationDefinition(operations)

    private fun append(operation: IMigrationApi<*>) {
        operations.add(operation)
    }
}

fun migration(block: MigrationDefinitionBuilder.() -> Unit): MigrationDefinition =
    MigrationDefinitionBuilder().apply(block).build()
