package gog.my_project.data_base.migration.ast.schema.add_column

import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst

class MigrationAddColumnAst : IMigrationAddColumnAst {
    override var tableName: String? = null
    override var columnAst: IMigrationColumnAst? = null
}