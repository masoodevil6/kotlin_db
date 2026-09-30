package gog.my_project.data_base.migration.dialect.nodes.rename_column

import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst

enum class RenameColumnSqlStrategy {
    NATIVE_RENAME,
    CHANGE_COLUMN,
}

/** Narrow handoff between migration execution preparation and rename SQL rendering. */
interface IRenameColumnSqlDialect {
    val renameColumnSqlStrategy: RenameColumnSqlStrategy

    /** `definitionSuffix` is the original source column definition after its identifier. */
    fun renderLegacyRenameColumn(
        ast: IMigrationRenameColumnAst,
        definitionSuffix: String,
    ): String?
}
