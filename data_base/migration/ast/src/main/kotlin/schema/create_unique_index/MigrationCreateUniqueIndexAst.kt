package gog.my_project.data_base.migration.ast.schema.create_unique_index

import gog.my_project.data_base.migration.ast.interfaces.create_unique_index.IMigrationCreateUniqueIndexAst

class MigrationCreateUniqueIndexAst : IMigrationCreateUniqueIndexAst {
    override var tableName: String? = null
    override var indexName: String? = null
    override var columnName: String? = null
}
