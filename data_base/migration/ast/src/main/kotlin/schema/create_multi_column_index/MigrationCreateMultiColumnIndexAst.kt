package gog.my_project.data_base.migration.ast.schema.create_multi_column_index

import gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexAst

class MigrationCreateMultiColumnIndexAst : IMigrationCreateMultiColumnIndexAst {
    override var tableName: String? = null
    override var indexName: String? = null
    override var columnNames: List<String>? = null
}
