package gog.my_project.data_base.migration.renderer.nodes.create_table.definition

import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.ForeignKeyDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.FullTextIndexDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.IMigrationCreateTableDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.IndexDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.PrimaryKeyDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.UniqueDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext

class MySqlMigrationCreateTableDefinitionCapability :
    IAstRenderer<IMigrationCreateTableDefinitionAst, MigrationDataClass?> {

    override fun render(
        ast: IMigrationCreateTableDefinitionAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String = when (ast) {
        is PrimaryKeyDefinitionAst -> {
            validateColumns(ast.columnNames, "PRIMARY KEY")
            "PRIMARY KEY (${renderColumns(ast.columnNames)})"
        }
        is UniqueDefinitionAst -> {
            validateIdentifier(ast.indexName, "UNIQUE name")
            validateColumns(ast.columnNames, "UNIQUE")
            "CONSTRAINT ${quoteIdentifier(ast.indexName)} UNIQUE (${renderColumns(ast.columnNames)})"
        }
        is IndexDefinitionAst -> {
            validateIdentifier(ast.indexName, "INDEX name")
            validateColumns(ast.columnNames, "INDEX")
            val method = ast.indexMethod?.let { " USING ${renderMethod(it)}" }.orEmpty()
            "INDEX ${quoteIdentifier(ast.indexName)} (${renderColumns(ast.columnNames)})$method"
        }
        is FullTextIndexDefinitionAst -> {
            validateIdentifier(ast.indexName, "FULLTEXT INDEX name")
            validateColumns(ast.columnNames, "FULLTEXT INDEX")
            "FULLTEXT INDEX ${quoteIdentifier(ast.indexName)} (${renderColumns(ast.columnNames)})"
        }
        is ForeignKeyDefinitionAst -> renderForeignKey(ast)
        else -> error("Unsupported CREATE TABLE definition: ${ast::class.qualifiedName}")
    }

    private fun renderForeignKey(ast: ForeignKeyDefinitionAst): String {
        validateIdentifier(ast.constraintName, "FOREIGN KEY name")
        validateIdentifier(ast.referencesTableName, "FOREIGN KEY referenced table")
        validateColumns(ast.columnNames, "FOREIGN KEY local")
        validateColumns(ast.referencesColumnNames, "FOREIGN KEY referenced")
        require(ast.columnNames.size == ast.referencesColumnNames.size) {
            "FOREIGN KEY local and referenced column lists must have equal size"
        }
        val actions = buildList {
            ast.onDelete?.let { add("ON DELETE ${renderAction(it)}") }
            ast.onUpdate?.let { add("ON UPDATE ${renderAction(it)}") }
        }
        val suffix = if (actions.isEmpty()) "" else " ${actions.joinToString(" ")}"
        return "CONSTRAINT ${quoteIdentifier(ast.constraintName)} " +
            "FOREIGN KEY (${renderColumns(ast.columnNames)}) " +
            "REFERENCES ${quoteIdentifier(ast.referencesTableName)} " +
            "(${renderColumns(ast.referencesColumnNames)})$suffix"
    }

    private fun validateIdentifier(value: String, label: String) {
        require(value.isNotBlank()) { "$label must not be blank" }
    }

    private fun validateColumns(columns: List<String>, label: String) {
        require(columns.isNotEmpty()) { "$label requires at least one column" }
        require(columns.all(String::isNotBlank)) { "$label column names must not be blank" }
        for (currentIndex in columns.indices) {
            for (previousIndex in 0 until currentIndex) {
                require(!columns[currentIndex].equals(columns[previousIndex], ignoreCase = true)) {
                    "$label column names must not contain duplicates"
                }
            }
        }
    }

    private fun renderColumns(columns: List<String>): String =
        columns.joinToString(", ", transform = ::quoteIdentifier)

    private fun renderMethod(method: IndexMethod): String = when (method) {
        IndexMethod.BTREE -> "BTREE"
        IndexMethod.HASH -> "HASH"
    }

    private fun renderAction(action: ForeignKeyAction): String = when (action) {
        ForeignKeyAction.CASCADE -> "CASCADE"
        ForeignKeyAction.RESTRICT -> "RESTRICT"
        ForeignKeyAction.NO_ACTION -> "NO ACTION"
        ForeignKeyAction.SET_NULL -> "SET NULL"
    }

    private fun quoteIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"
}
