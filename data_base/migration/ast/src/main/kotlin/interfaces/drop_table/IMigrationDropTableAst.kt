package gog.my_project.data_base.migration.ast.interfaces.drop_table

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationDropTableAst : IMigrationAst {

    var tableName: String?

    var ifExists: Boolean
}