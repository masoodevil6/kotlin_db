package gog.my_project.data_base.migration.ast.schema.create_index

import gog.my_project.data_base.migration.ast.interfaces.create_index.IMigrationCreateIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod

class MigrationCreateIndexAst : IMigrationCreateIndexAst {
    override var tableName: String? = null
    override var indexName: String? = null
    override var columnName: String? = null
    override var indexMethod: IndexMethod? = null
}
