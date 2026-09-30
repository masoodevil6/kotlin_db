package gog.my_project.data_base.migration.ast.schema.create_table.column

import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

class MigrationColumnAst : IMigrationColumnAst {

    override var columnName: String? =           null;
    override var columnDataType: MigrationColumnDataType? = null;
    override var columnDefault: Any? =           null;
    override var hasColumnDefault: Boolean =      false;
    override var columnNullable: Boolean =       false;
    override var columnAutoIncrement: Boolean =  false;
    override var columnPrimary: Boolean =        false;

}
