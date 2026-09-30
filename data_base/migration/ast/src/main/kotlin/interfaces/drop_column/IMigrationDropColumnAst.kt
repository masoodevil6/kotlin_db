package gog.my_project.data_base.migration.ast.interfaces.drop_column

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationDropColumnAst : IMigrationAst {
    var tableName: String?

    var name: String?

    var ifExists: Boolean
}
