package gog.my_project.data_base.migration.renderer.nodes.create_table.column

import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.dialect.data_class.create_table.column.MigrationColumnData
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_table.column.IMigrationColumnCapability
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

class MySqlMigrationColumnCapability : IMigrationColumnCapability {

    override fun render(
        ast: IMigrationColumnAst,
        ctx: IRenderContext,
        dataClass: MigrationColumnData?
    ): String {
        val name = requireNotNull(ast.columnName?.takeIf(String::isNotBlank)) {
            "Column name must not be blank"
        }
        val type = requireNotNull(ast.columnDataType) { "Column '$name' must have a data type" }
        val typeSql = renderDataType(type)
        val parts = mutableListOf(quoteIdentifier(name), typeSql)

        parts += if (ast.columnNullable) "NULL" else "NOT NULL"
        if (ast.hasColumnDefault) {
            validateTemporalDefault(type, ast.columnDefault)
            parts += "DEFAULT ${renderDefault(ast.columnDefault)}"
        }
        if (ast.columnAutoIncrement) parts += "AUTO_INCREMENT"
        if (ast.columnPrimary) parts += "PRIMARY KEY"

        return parts.joinToString(" ")
    }

    private fun renderDataType(type: MigrationColumnDataType): String = when (type) {
        is DateType -> "DATE"
        is TimeType -> renderTemporalType("TIME", type.precision)
        is DateTimeType -> renderTemporalType("DATETIME", type.precision)
        is TimestampType -> renderTemporalType("TIMESTAMP", type.precision)
        is VarcharType -> "VARCHAR(${type.length})"
        is TextType -> "TEXT"
        is IntType -> "INT"
        is BooleanType -> "TINYINT(1)"
        is DecimalType -> "DECIMAL(${type.precision}, ${type.scale})"
        is JsonType -> "JSON"
    }

    private fun renderTemporalType(name: String, precision: Int?): String {
        require(precision == null || precision in 0..6) {
            "$name fractional-second precision must be between 0 and 6"
        }
        return "$name${precision?.let { "($it)" } ?: ""}"
    }

    private fun validateTemporalDefault(type: MigrationColumnDataType, value: Any?) {
        if (type is DateType || type is TimeType || type is DateTimeType || type is TimestampType) {
            require(value == null || value is String) {
                "Temporal column DEFAULT must be a string literal or NULL"
            }
        }
    }

    private fun renderDefault(value: Any?): String = when (value) {
        null -> "NULL"
        is String -> "'${value.replace("'", "''")}'"
        is Boolean -> if (value) "1" else "0"
        is Byte, is Short, is Int, is Long -> value.toString()
        is Float -> value.toDouble().takeIf(Double::isFinite)?.toString()
            ?: throw IllegalArgumentException("DEFAULT must be a finite number")
        is Double -> value.takeIf(Double::isFinite)?.toString()
            ?: throw IllegalArgumentException("DEFAULT must be a finite number")
        else -> throw IllegalArgumentException(
            "Unsupported DEFAULT value type: ${value::class.qualifiedName}"
        )
    }

    private fun quoteIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"
}
