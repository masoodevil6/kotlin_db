package gog.my_project.data_base_schema.dialect.nodes.rename_column

import gog.my_project.data_base_schema.ast.interfaces.rename_column.ISchemaRenameColumnAst

enum class RenameColumnSqlStrategy {
    NATIVE_RENAME,
    CHANGE_COLUMN,
}

/** Narrow handoff between migration execution preparation and rename SQL rendering. */
interface IRenameColumnSqlDialect {
    val renameColumnSqlStrategy: RenameColumnSqlStrategy

    /** `definitionSuffix` is the original source column definition after its identifier. */
    fun renderLegacyRenameColumn(
        ast: ISchemaRenameColumnAst,
        definitionSuffix: String,
    ): String?
}
