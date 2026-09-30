package gog.my_project.data_base.migration.ast.schema.drop_table

import gog.my_project.data_base.migration.ast.interfaces.drop_table.IMigrationDropTableAst

class MigrationDropTableAst : IMigrationDropTableAst {

    override var tableName: String? = null

    override var ifExists: Boolean = false
}