package gog.my_project.data_base.migration.ast.interfaces.create_full_text_index

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationCreateFullTextIndexAst : IMigrationAst {
    var tableName: String?
    var indexName: String?
    var columnNames: List<String>?
}
