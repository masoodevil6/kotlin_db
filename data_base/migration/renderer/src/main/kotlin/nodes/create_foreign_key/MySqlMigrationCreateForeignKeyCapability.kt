package gog.my_project.data_base.migration.renderer.nodes.create_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.create_foreign_key.IMigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_foreign_key.IMigrationCreateForeignKeyCapability

class MySqlMigrationCreateForeignKeyCapability : IMigrationCreateForeignKeyCapability {
    override fun render(
        ast: IMigrationCreateForeignKeyAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val table = requireIdentifier(ast.tableName, "table")
        val constraint = requireIdentifier(ast.constraintName, "constraint")
        val referencedTable = requireIdentifier(ast.referencesTableName, "referenced table")
        val localColumns = requireColumns(ast.columnNames, "local")
        val referencedColumns = requireColumns(ast.referencesColumnNames, "referenced")
        require(localColumns.size == referencedColumns.size) {
            "CREATE FOREIGN KEY local and referenced column lists must have equal size"
        }

        val sql = StringBuilder()
            .append("ALTER TABLE ").append(quoteIdentifier(table))
            .append(" ADD CONSTRAINT ").append(quoteIdentifier(constraint))
            .append(" FOREIGN KEY (").append(localColumns.joinToString(", ", transform = ::quoteIdentifier)).append(')')
            .append(" REFERENCES ").append(quoteIdentifier(referencedTable))
            .append(" (").append(referencedColumns.joinToString(", ", transform = ::quoteIdentifier)).append(')')

        ast.onDelete?.let { sql.append(" ON DELETE ").append(it.sql()) }
        ast.onUpdate?.let { sql.append(" ON UPDATE ").append(it.sql()) }
        return sql.toString()
    }

    private fun requireColumns(columns: List<String>?, label: String): List<String> {
        val values = requireNotNull(columns) { "CREATE FOREIGN KEY requires $label columns" }
        require(values.isNotEmpty()) { "CREATE FOREIGN KEY requires at least one $label column" }
        require(values.all(String::isNotBlank)) { "CREATE FOREIGN KEY $label column names must be non-blank" }
        for (currentIndex in values.indices) {
            for (previousIndex in 0 until currentIndex) {
                require(!values[currentIndex].trim().equals(values[previousIndex].trim(), ignoreCase = true)) {
                    "CREATE FOREIGN KEY $label column names must not contain duplicates"
                }
            }
        }
        return values
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) {
            "CREATE FOREIGN KEY requires a non-blank $label name"
        }

    private fun ForeignKeyAction.sql(): String = when (this) {
        ForeignKeyAction.CASCADE -> "CASCADE"
        ForeignKeyAction.RESTRICT -> "RESTRICT"
        ForeignKeyAction.NO_ACTION -> "NO ACTION"
        ForeignKeyAction.SET_NULL -> "SET NULL"
    }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
