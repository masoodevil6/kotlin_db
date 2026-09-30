package gog.my_project.data_base.migration.ast.interfaces.create_table.column

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

interface IMigrationColumnAst : IMigrationAst {

    var columnName :           String?;
    var columnDataType :       MigrationColumnDataType?;
    var columnDefault :        Any?;
    var hasColumnDefault :     Boolean;
    var columnNullable :       Boolean;
    var columnAutoIncrement :  Boolean;
    var columnPrimary :        Boolean;

}
