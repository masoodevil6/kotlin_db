package gog.my_project.data_base_schema

import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTablePrimaryKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableUniqueApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.create_table.table.ISchemaTableApi
import gog.my_project.data_base_schema.api.interfaces.create_table.table_column.ISchemaColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi
import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base_schema.params.data_types.BooleanType
import gog.my_project.data_base_schema.params.data_types.DateTimeType
import gog.my_project.data_base_schema.params.data_types.DateType
import gog.my_project.data_base_schema.params.data_types.DecimalType
import gog.my_project.data_base_schema.params.data_types.IntType
import gog.my_project.data_base_schema.params.data_types.JsonType
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType
import gog.my_project.data_base_schema.params.data_types.TextType
import gog.my_project.data_base_schema.params.data_types.TimeType
import gog.my_project.data_base_schema.params.data_types.TimestampType
import gog.my_project.data_base_schema.params.data_types.VarcharType

internal fun SchemaDataType.toInternal(): SchemaColumnDataType = when (this) {
    SchemaDataType.IntType -> IntType()
    SchemaDataType.BooleanType -> BooleanType()
    SchemaDataType.DateType -> DateType()
    SchemaDataType.TextType -> TextType()
    SchemaDataType.JsonType -> JsonType()
    is SchemaDataType.VarcharType -> VarcharType(length)
    is SchemaDataType.DecimalType -> DecimalType(precision, scale)
    is SchemaDataType.DateTimeType -> DateTimeType(precision)
    is SchemaDataType.TimestampType -> TimestampType(precision)
    is SchemaDataType.TimeType -> TimeType(precision)
}

private fun SchemaIndexMethod.toInternal(): IndexMethod = IndexMethod.valueOf(name)
private fun SchemaForeignKeyAction.toInternal(): ForeignKeyAction = ForeignKeyAction.valueOf(name)

internal class TableDefinitionAdapter(private val delegate: ISchemaTableApi) : SchemaTableDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
}

internal class ColumnDefinitionAdapter(private val delegate: ISchemaColumnApi) : SchemaColumnDefinition {
    override fun name(name: String) { delegate.name(name) }
    override fun dataType(dataType: SchemaDataType) { delegate.dataType(dataType.toInternal()) }
    override fun default(value: Any?) { delegate.default(value) }
    override fun isNullable(nullable: Boolean) { delegate.isNullable(nullable) }
    override fun isAutoIncrement(enabled: Boolean) { delegate.isAutoIncrement(enabled) }
    override fun isPrimary(enabled: Boolean) { delegate.isPrimary(enabled) }
}

internal class CreateTablePrimaryKeyAdapter(
    private val delegate: ISchemaCreateTablePrimaryKeyApi,
) : SchemaCreateTablePrimaryKeyDefinition {
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
}

internal class CreateTableUniqueAdapter(
    private val delegate: ISchemaCreateTableUniqueApi,
) : SchemaCreateTableUniqueDefinition {
    override fun name(name: String) { delegate.name(name) }
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
}

internal class CreateTableIndexAdapter(
    private val delegate: ISchemaCreateTableIndexApi,
) : SchemaCreateTableIndexDefinition {
    override fun name(name: String) { delegate.name(name) }
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
    override fun using(method: SchemaIndexMethod) { delegate.using(method.toInternal()) }
}

internal class CreateTableFullTextIndexAdapter(
    private val delegate: ISchemaCreateTableFullTextIndexApi,
) : SchemaCreateTableFullTextIndexDefinition {
    override fun name(name: String) { delegate.name(name) }
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
}

internal class ForeignKeyAdapter(
    private val delegate: ISchemaCreateTableForeignKeyApi,
) : SchemaForeignKeyDefinition {
    override fun name(name: String) { delegate.name(name) }
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
    override fun referencesTable(table: String) { delegate.referencesTable(table) }
    override fun referencesColumns(vararg columns: String) { delegate.referencesColumns(*columns) }
    override fun onDelete(action: SchemaForeignKeyAction) { delegate.onDelete(action.toInternal()) }
    override fun onUpdate(action: SchemaForeignKeyAction) { delegate.onUpdate(action.toInternal()) }
}

internal class CreateTableDefinitionAdapter(
    private val delegate: ISchemaRenderCreateTableApi,
) : SchemaCreateTableDefinition {
    override fun table(block: SchemaTableDefinition.() -> Unit) {
        delegate.table { TableDefinitionAdapter(this).block() }
    }

    override fun addColumn(block: SchemaColumnDefinition.() -> Unit) {
        delegate.addColumn { ColumnDefinitionAdapter(this).block() }
    }

    override fun timestamps() { delegate.timestamps() }
    override fun softDeletes() { delegate.softDeletes() }

    override fun primaryKey(block: SchemaCreateTablePrimaryKeyDefinition.() -> Unit) {
        delegate.primaryKey { CreateTablePrimaryKeyAdapter(this).block() }
    }

    override fun unique(block: SchemaCreateTableUniqueDefinition.() -> Unit) {
        delegate.unique { CreateTableUniqueAdapter(this).block() }
    }

    override fun index(block: SchemaCreateTableIndexDefinition.() -> Unit) {
        delegate.index { CreateTableIndexAdapter(this).block() }
    }

    override fun fullTextIndex(block: SchemaCreateTableFullTextIndexDefinition.() -> Unit) {
        delegate.fullTextIndex { CreateTableFullTextIndexAdapter(this).block() }
    }

    override fun foreignKey(block: SchemaForeignKeyDefinition.() -> Unit) {
        delegate.foreignKey { ForeignKeyAdapter(this).block() }
    }

    override fun ifNotExists() { delegate.ifNotExists() }
}

internal class AddColumnDefinitionAdapter(
    private val delegate: ISchemaAddColumnApi,
) : SchemaAddColumnDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun dataType(dataType: SchemaDataType) { delegate.dataType(dataType.toInternal()) }
    override fun default(value: Any?) { delegate.default(value) }
    override fun isNullable(nullable: Boolean) { delegate.isNullable(nullable) }
    override fun isAutoIncrement(enabled: Boolean) { delegate.isAutoIncrement(enabled) }
    override fun isPrimary(enabled: Boolean) { delegate.isPrimary(enabled) }
}

internal class DropTableDefinitionAdapter(private val delegate: ISchemaDropTableApi) : SchemaDropTableDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun ifExists() { delegate.ifExists() }
}

internal class RenameTableDefinitionAdapter(private val delegate: ISchemaRenameTableApi) : SchemaRenameTableDefinition {
    override fun fromTableName(table: String) { delegate.fromTableName(table) }
    override fun toTableName(table: String) { delegate.toTableName(table) }
}

internal class DropColumnDefinitionAdapter(private val delegate: ISchemaDropColumnApi) : SchemaDropColumnDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun ifExists() { delegate.ifExists() }
}

internal class RenameColumnDefinitionAdapter(private val delegate: ISchemaRenameColumnApi) : SchemaRenameColumnDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun to(name: String) { delegate.to(name) }
}

internal class ModifyColumnDefinitionAdapter(private val delegate: ISchemaModifyColumnApi) : SchemaModifyColumnDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun dataType(dataType: SchemaDataType) { delegate.dataType(dataType.toInternal()) }
    override fun default(value: Any?) { delegate.default(value) }
    override fun isNullable(nullable: Boolean) { delegate.isNullable(nullable) }
    override fun autoIncrement(enabled: Boolean) { delegate.autoIncrement(enabled) }
}

internal class CreateIndexDefinitionAdapter(private val delegate: ISchemaCreateIndexApi) : SchemaCreateIndexDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun column(column: String) { delegate.column(column) }
    override fun using(method: SchemaIndexMethod) { delegate.using(method.toInternal()) }
}

internal class DropIndexDefinitionAdapter(private val delegate: ISchemaDropIndexApi) : SchemaDropIndexDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
}

internal class CreateUniqueIndexDefinitionAdapter(
    private val delegate: ISchemaCreateUniqueIndexApi,
) : SchemaCreateUniqueIndexDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun column(column: String) { delegate.column(column) }
}

internal class CreateMultiColumnIndexDefinitionAdapter(
    private val delegate: ISchemaCreateMultiColumnIndexApi,
) : SchemaCreateMultiColumnIndexDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
}

internal class CreateFullTextIndexDefinitionAdapter(
    private val delegate: ISchemaCreateFullTextIndexApi,
) : SchemaCreateFullTextIndexDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
}

internal class CreateSpatialIndexDefinitionAdapter(
    private val delegate: ISchemaCreateSpatialIndexApi,
) : SchemaCreateSpatialIndexDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun column(column: String) { delegate.column(column) }
}

internal class CreateForeignKeyDefinitionAdapter(
    private val delegate: ISchemaCreateForeignKeyApi,
) : SchemaCreateForeignKeyDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
    override fun columns(vararg columns: String) { delegate.columns(*columns) }
    override fun referencesTable(table: String) { delegate.referencesTable(table) }
    override fun referencesColumns(vararg columns: String) { delegate.referencesColumns(*columns) }
    override fun onDelete(action: SchemaForeignKeyAction) { delegate.onDelete(ForeignKeyAction.valueOf(action.name)) }
    override fun onUpdate(action: SchemaForeignKeyAction) { delegate.onUpdate(ForeignKeyAction.valueOf(action.name)) }
}

internal class DropForeignKeyDefinitionAdapter(
    private val delegate: ISchemaDropForeignKeyApi,
) : SchemaDropForeignKeyDefinition {
    override fun tableName(table: String) { delegate.tableName(table) }
    override fun name(name: String) { delegate.name(name) }
}
