package gog.my_project.data_base.migration.ast.interfaces.add_column

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst

interface IMigrationAddColumnAst : IMigrationAst {
    var tableName: String?
    var columnAst: IMigrationColumnAst?
}