package gog.my_project.data_base.migration.ast.schema.rename_table

import gog.my_project.data_base.migration.ast.interfaces.rename_table.IMigrationRenameTableAst

class MigrationRenameTableAst : IMigrationRenameTableAst {

    override var fromTableName: String? = null

    override var toTableName: String? = null
}