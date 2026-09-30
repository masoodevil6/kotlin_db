package gog.my_project.data_base.migration.ast.schema.drop_column

import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst

class MigrationDropColumnAst : IMigrationDropColumnAst {
    override var tableName: String? = null

    override var name: String? = null

    override var ifExists: Boolean = false
}
