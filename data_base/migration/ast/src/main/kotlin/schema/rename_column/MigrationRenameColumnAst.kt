package gog.my_project.data_base.migration.ast.schema.rename_column

import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst

class MigrationRenameColumnAst : IMigrationRenameColumnAst {
    override var tableName: String? = null
    override var name: String? = null
    override var to: String? = null
}
