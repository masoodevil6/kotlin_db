package gog.my_project.data_base.migration.ast.interfaces.rename_table

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationRenameTableAst : IMigrationAst {

    var fromTableName: String?

    var toTableName: String?
}