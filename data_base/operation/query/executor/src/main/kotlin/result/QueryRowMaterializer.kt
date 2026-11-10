package gog.my_project.data_base_query.executor.result

import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.ast.interfaces.select_interface.column.IQueryColumnsAst
import java.sql.ResultSet

/** Shared output-alias and value materialization for JDBC and Operation-scoped row sources. */
internal object QueryRowMaterializer {
    fun materialize(resultSet: ResultSet, outputColumns: List<IQueryColumnsAst>): QueryRow {
        val metadata = resultSet.metaData
        check(metadata.columnCount == outputColumns.size) {
            "ResultSet column count does not match SELECT output count"
        }
        val values = outputColumns.mapIndexed { index, outputColumn ->
            val label = outputLabel(metadata.getColumnLabel(index + 1), index + 1)
            val expectedAlias = requireNotNull(outputColumn.metadata.ColumnAlias)
            check(label == expectedAlias) {
                "ResultSet column label '$label' does not match SELECT output alias '$expectedAlias'"
            }
            label to readValue(resultSet, index + 1, outputColumn)
        }
        return QueryRow(values, propertyAliases(outputColumns))
    }

    fun materialize(rows: OperationQueryRows, outputColumns: List<IQueryColumnsAst>): QueryRow {
        val labels = rows.columnLabels
        check(labels.size == outputColumns.size) {
            "ResultSet column count does not match SELECT output count"
        }
        val values = outputColumns.mapIndexed { index, outputColumn ->
            val label = outputLabel(labels[index], index + 1)
            val expectedAlias = requireNotNull(outputColumn.metadata.ColumnAlias)
            check(label == expectedAlias) {
                "ResultSet column label '$label' does not match SELECT output alias '$expectedAlias'"
            }
            label to readValue(rows, label, outputColumn)
        }
        return QueryRow(values, propertyAliases(outputColumns))
    }

    private fun outputLabel(label: String, index: Int): String {
        check(label.isNotBlank()) { "ResultSet column $index has no SQL output alias" }
        return label
    }

    private fun readValue(
        resultSet: ResultSet,
        index: Int,
        outputColumn: IQueryColumnsAst,
    ): Any? = when (requireNotNull(outputColumn.metadata.ExecutionType)) {
        DataType.LONG -> resultSet.getLong(index).let { if (resultSet.wasNull()) null else it }
        DataType.INT -> resultSet.getInt(index).let { if (resultSet.wasNull()) null else it }
        DataType.STRING -> resultSet.getString(index)
    }

    private fun readValue(
        rows: OperationQueryRows,
        alias: String,
        outputColumn: IQueryColumnsAst,
    ): Any? = when (requireNotNull(outputColumn.metadata.ExecutionType)) {
        DataType.LONG -> rows.getLong(alias)
        DataType.INT -> rows.getInt(alias)
        DataType.STRING -> rows.getString(alias)
    }

    private fun propertyAliases(outputColumns: List<IQueryColumnsAst>) =
        outputColumns.mapNotNull { outputColumn ->
            val property = outputColumn.metadata.PropertyReference ?: return@mapNotNull null
            property to requireNotNull(outputColumn.metadata.ColumnAlias)
        }
}
