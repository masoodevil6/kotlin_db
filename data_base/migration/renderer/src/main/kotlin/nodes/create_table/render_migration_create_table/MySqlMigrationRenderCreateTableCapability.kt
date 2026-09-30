package gog.my_project.data_base.migration.renderer.nodes.create_table.render_migration_create_table

import gog.my_project.data_base.migration.ast.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.PrimaryKeyDefinitionAst
import gog.my_project.data_base.migration.dialect.data_class.create_table.column.MigrationColumnData
import gog.my_project.data_base.migration.dialect.data_class.create_table.render_migration_create_table.MigrationRenderCreateTableData
import gog.my_project.data_base.migration.dialect.data_class.create_table.table.MigrationTableData
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_table.render_migration_create_table.IMigrationRenderCreateTableCapability
import gog.my_project.data_base.migration.params.data_types.BooleanType
import gog.my_project.data_base.migration.params.data_types.DecimalType
import gog.my_project.data_base.migration.params.data_types.DateType
import gog.my_project.data_base.migration.params.data_types.DateTimeType
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.JsonType
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType
import gog.my_project.data_base.migration.params.data_types.TimeType
import gog.my_project.data_base.migration.params.data_types.TimestampType
import gog.my_project.data_base.migration.params.data_types.TextType
import gog.my_project.data_base.migration.params.data_types.VarcharType
import java.util.Locale

class MySqlMigrationRenderCreateTableCapability : IMigrationRenderCreateTableCapability {

    override fun render(
        ast: IMigrationRenderCreateTableAst,
        ctx: IRenderContext,
        dataClass: MigrationRenderCreateTableData?
    ): String {
        val tableAst = requireNotNull(ast.migrationTableAst) { "CREATE TABLE requires a table name" }
        val tableName = requireNotNull(
            ctx.registry.render(tableAst, ctx.dialect, MigrationTableData())
                ?.takeIf(String::isNotBlank)
        ) { "CREATE TABLE requires a non-blank table name" }
        require(ast.columns.isNotEmpty()) { "CREATE TABLE '$tableName' requires at least one column" }

        val names = ast.columns.mapIndexed { index, column ->
            requireNotNull(column.columnName?.takeIf(String::isNotBlank)) {
                "Column at index $index must have a non-blank name"
            }
        }
        val normalizedNames = names.map { it.trim().lowercase(Locale.ROOT) }
        require(normalizedNames.distinct().size == normalizedNames.size) {
            "CREATE TABLE '$tableName' has duplicate column names"
        }
        require(ast.columns.count { it.columnPrimary } <= 1) {
            "CREATE TABLE '$tableName' supports at most one column-level primary key"
        }
        val tablePrimaryKeyCount = ast.definitions.count { it is PrimaryKeyDefinitionAst }
        require(tablePrimaryKeyCount <= 1) { "CREATE TABLE '$tableName' supports only one primary key" }
        require(!(tablePrimaryKeyCount > 0 && ast.columns.any { it.columnPrimary })) {
            "CREATE TABLE '$tableName' cannot combine column-level and table-level primary keys"
        }
        val tablePrimaryKey = ast.definitions.filterIsInstance<PrimaryKeyDefinitionAst>().singleOrNull()

        ast.columns.forEachIndexed { index, column ->
            val name = names[index]
            val type = requireNotNull(column.columnDataType) { "Column '$name' must have a data type" }
            val belongsToTablePrimaryKey = tablePrimaryKey?.columnNames?.any {
                it.equals(name, ignoreCase = true)
            } == true
            if (column.columnAutoIncrement) {
                require(type is IntType) { "AUTO_INCREMENT column '$name' must use IntType" }
                require(column.columnPrimary || belongsToTablePrimaryKey) {
                    "AUTO_INCREMENT column '$name' must be a primary key"
                }
            }
            require(!((column.columnPrimary || belongsToTablePrimaryKey) && column.columnNullable)) {
                "Primary key column '$name' cannot be nullable"
            }
            if (column.hasColumnDefault) validateDefault(name, type, column.columnDefault)
        }

        val columns = ast.columns.map { column ->
            requireNotNull(ctx.registry.render(column, ctx.dialect, MigrationColumnData())) {
                "Renderer returned no SQL for column '${column.columnName}'"
            }
        }
        val definitions = ast.definitions.map { definition ->
            requireNotNull(ctx.registry.render(definition, ctx.dialect)) {
                "Renderer returned no SQL for CREATE TABLE definition '${definition::class.simpleName}'"
            }
        }
        val allDefinitions = columns + definitions
        val ifNotExists = if (ast.ifNotExists) " IF NOT EXISTS" else ""
        return "${ctx.dialect._prefixCreateTable}$ifNotExists $tableName (\n  ${allDefinitions.joinToString(",\n  ")}\n)"
    }

    private fun validateDefault(name: String, type: MigrationColumnDataType, value: Any?) {
        val valid = when (type) {
            is IntType -> value == null || value is Byte || value is Short || value is Int || value is Long
            is DecimalType -> value == null || value is Number
            is BooleanType -> value == null || value is Boolean || value.isBooleanIntegerDefault()
            is VarcharType -> value == null || value is String
            is DateType, is TimeType, is DateTimeType, is TimestampType -> value == null || value is String
            // MySQL restricts literal defaults for TEXT and JSON across supported versions.
            // Expression defaults need an explicit AST type and are not accepted as raw strings.
            is TextType, is JsonType -> value == null
        }
        require(valid) { "DEFAULT value is incompatible with column '$name'" }
    }

    private fun Any?.isBooleanIntegerDefault(): Boolean = when (this) {
        is Byte -> this.toInt() in 0..1
        is Short -> this.toInt() in 0..1
        is Int -> this in 0..1
        else -> false
    }
}
