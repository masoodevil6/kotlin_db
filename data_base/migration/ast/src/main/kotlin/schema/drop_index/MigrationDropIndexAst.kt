package gog.my_project.data_base.migration.ast.schema.drop_index

import gog.my_project.data_base.migration.ast.interfaces.drop_index.IMigrationDropIndexAst

class MigrationDropIndexAst : IMigrationDropIndexAst {
    override var tableName: String? = null
    override var indexName: String? = null
}
