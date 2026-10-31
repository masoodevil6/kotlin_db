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
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val constraint = requireIdentifier(ast.metadata.constraintName, "constraint")
        val referencedTable = requireIdentifier(ast.metadata.referencesTableName, "referenced table")
        val localColumns = requireColumns(ast.metadata.columnNames, "local")
        val referencedColumns = requireColumns(ast.metadata.referencesColumnNames, "referenced")

        val sql = StringBuilder()
            .append("ALTER TABLE ").append(quoteIdentifier(table))
            .append(" ADD CONSTRAINT ").append(quoteIdentifier(constraint))
            .append(" FOREIGN KEY (").append(localColumns.joinToString(", ", transform = ::quoteIdentifier)).append(')')
            .append(" REFERENCES ").append(quoteIdentifier(referencedTable))
            .append(" (").append(referencedColumns.joinToString(", ", transform = ::quoteIdentifier)).append(')')

        ast.metadata.onDelete?.let { sql.append(" ON DELETE ").append(it.sql()) }
        ast.metadata.onUpdate?.let { sql.append(" ON UPDATE ").append(it.sql()) }
        return sql.toString()
    }

    private fun requireColumns(columns: List<String>?, label: String): List<String> {
        val values = requireNotNull(columns) { "CREATE FOREIGN KEY requires $label columns" }
        require(values.isNotEmpty()) { "CREATE FOREIGN KEY requires at least one $label column" }
        return values
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "CREATE FOREIGN KEY requires a $label name" }

    private fun ForeignKeyAction.sql(): String = when (this) {
        ForeignKeyAction.CASCADE -> "CASCADE"
        ForeignKeyAction.RESTRICT -> "RESTRICT"
        ForeignKeyAction.NO_ACTION -> "NO ACTION"
        ForeignKeyAction.SET_NULL -> "SET NULL"
    }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
