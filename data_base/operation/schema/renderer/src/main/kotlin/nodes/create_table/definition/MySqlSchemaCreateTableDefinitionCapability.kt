package gog.my_project.data_base_schema.renderer.nodes.create_table.definition

import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.ForeignKeyDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.FullTextIndexDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.ISchemaCreateTableDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.IndexDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.PrimaryKeyDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.UniqueDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext

class MySqlSchemaCreateTableDefinitionCapability :
    IAstRenderer<ISchemaCreateTableDefinitionAst<*>, SchemaDataClass?> {

    override fun render(
        ast: ISchemaCreateTableDefinitionAst<*>,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String = when (ast) {
        is PrimaryKeyDefinitionAst -> {
            validateColumns(ast.metadata.columnNames, "PRIMARY KEY")
            "PRIMARY KEY (${renderColumns(ast.metadata.columnNames)})"
        }
        is UniqueDefinitionAst -> {
            validateColumns(ast.metadata.columnNames, "UNIQUE")
            "CONSTRAINT ${quoteIdentifier(ast.metadata.indexName)} UNIQUE (${renderColumns(ast.metadata.columnNames)})"
        }
        is IndexDefinitionAst -> {
            validateColumns(ast.metadata.columnNames, "INDEX")
            val method = ast.metadata.indexMethod?.let { " USING ${renderMethod(it)}" }.orEmpty()
            "INDEX ${quoteIdentifier(ast.metadata.indexName)} (${renderColumns(ast.metadata.columnNames)})$method"
        }
        is FullTextIndexDefinitionAst -> {
            validateColumns(ast.metadata.columnNames, "FULLTEXT INDEX")
            "FULLTEXT INDEX ${quoteIdentifier(ast.metadata.indexName)} (${renderColumns(ast.metadata.columnNames)})"
        }
        is ForeignKeyDefinitionAst -> renderForeignKey(ast)
        else -> error("Unsupported CREATE TABLE definition: ${ast::class.qualifiedName}")
    }

    private fun renderForeignKey(ast: ForeignKeyDefinitionAst): String {
        validateColumns(ast.metadata.columnNames, "FOREIGN KEY local")
        validateColumns(ast.metadata.referencesColumnNames, "FOREIGN KEY referenced")
        val actions = buildList {
            ast.metadata.onDelete?.let { add("ON DELETE ${renderAction(it)}") }
            ast.metadata.onUpdate?.let { add("ON UPDATE ${renderAction(it)}") }
        }
        val suffix = if (actions.isEmpty()) "" else " ${actions.joinToString(" ")}"
        return "CONSTRAINT ${quoteIdentifier(ast.metadata.constraintName)} " +
            "FOREIGN KEY (${renderColumns(ast.metadata.columnNames)}) " +
            "REFERENCES ${quoteIdentifier(ast.metadata.referencesTableName)} " +
            "(${renderColumns(ast.metadata.referencesColumnNames)})$suffix"
    }

    private fun validateColumns(columns: List<String>, label: String) {
        require(columns.isNotEmpty()) { "$label requires at least one column" }
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
