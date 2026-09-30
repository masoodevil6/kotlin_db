package gog.my_project.data_base.migration.renderer.nodes.modify_column

import gog.my_project.data_base.migration.ast.interfaces.modify_column.ColumnNullability
import gog.my_project.data_base.migration.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base.migration.ast.interfaces.modify_column.IMigrationModifyColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.modify_column.IMigrationModifyColumnCapability
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

class MySqlMigrationModifyColumnCapability : IMigrationModifyColumnCapability {
    override fun render(
        ast: IMigrationModifyColumnAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val table = requireIdentifier(ast.tableName, "table")
        val name = requireIdentifier(ast.columnName, "column")
        val typeSql = renderType(ast.columnDataType)
        val parts = mutableListOf(quoteIdentifier(name), typeSql)
        parts += when (ast.nullability) {
            ColumnNullability.NULL -> "NULL"
            ColumnNullability.NOT_NULL -> "NOT NULL"
        }
        when (val default = ast.defaultDefinition) {
            DefaultDefinition.NoDefaultClause -> Unit
            DefaultDefinition.DefaultNull -> parts += "DEFAULT NULL"
            is DefaultDefinition.DefaultValue -> {
                validateTemporalDefault(ast.columnDataType, default.value)
                parts += "DEFAULT ${renderDefault(default.value)}"
            }
        }
        if (ast.autoIncrement) parts += "AUTO_INCREMENT"
        return "ALTER TABLE ${quoteIdentifier(table)} MODIFY COLUMN ${parts.joinToString(" ")}"
    }

    private fun renderType(type: MigrationColumnDataType): String = when (type) {
        is DateType -> "DATE"
        is TimeType -> renderTemporalType("TIME", type.precision)
        is DateTimeType -> renderTemporalType("DATETIME", type.precision)
        is TimestampType -> renderTemporalType("TIMESTAMP", type.precision)
        is VarcharType -> "VARCHAR(${type.length.also { require(it > 0) { "VARCHAR length must be positive" } }})"
        is TextType -> "TEXT"
        is IntType -> "INT"
        is BooleanType -> "TINYINT(1)"
        is DecimalType -> {
            require(type.precision > 0 && type.scale in 0..type.precision) { "Invalid DECIMAL precision/scale" }
            "DECIMAL(${type.precision}, ${type.scale})"
        }
        is JsonType -> "JSON"
    }

    private fun renderTemporalType(name: String, precision: Int?): String {
        require(precision == null || precision in 0..6) {
            "$name fractional-second precision must be between 0 and 6"
        }
        return "$name${precision?.let { "($it)" } ?: ""}"
    }

    private fun validateTemporalDefault(type: MigrationColumnDataType, value: Any) {
        if (type is DateType || type is TimeType || type is DateTimeType || type is TimestampType) {
            require(value is String) { "Temporal column DEFAULT must be a string literal" }
        }
    }

    private fun renderDefault(value: Any): String = when (value) {
        is String -> "'${value.replace("'", "''")}'"
        is Boolean -> if (value) "1" else "0"
        is Byte, is Short, is Int, is Long -> value.toString()
        is Float -> value.toDouble().takeIf(Double::isFinite)?.toString()
            ?: throw IllegalArgumentException("DEFAULT must be a finite number")
        is Double -> value.takeIf(Double::isFinite)?.toString()
            ?: throw IllegalArgumentException("DEFAULT must be a finite number")
        else -> throw IllegalArgumentException("Unsupported DEFAULT value type: ${value::class.qualifiedName}")
    }

    private fun requireIdentifier(value: String, label: String): String =
        requireNotNull(value.trim().takeIf(String::isNotBlank)) { "MODIFY COLUMN requires a non-blank $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
