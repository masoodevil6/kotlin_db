package gog.my_project.data_base.migration.ast.interfaces.rename_column

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationRenameColumnAst : IMigrationAst {
    var tableName: String?
    var name: String?
    var to: String?
}
