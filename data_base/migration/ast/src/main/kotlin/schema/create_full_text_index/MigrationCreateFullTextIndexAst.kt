package gog.my_project.data_base.migration.ast.schema.create_full_text_index

import gog.my_project.data_base.migration.ast.interfaces.create_full_text_index.IMigrationCreateFullTextIndexAst

class MigrationCreateFullTextIndexAst : IMigrationCreateFullTextIndexAst {
    override var tableName: String? = null
    override var indexName: String? = null
    override var columnNames: List<String>? = null
}
