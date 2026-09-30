package gog.my_project.data_base.migration.ast.interfaces.create_table.table

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationTableAst : IMigrationAst {

    var tableName :           String?;

}