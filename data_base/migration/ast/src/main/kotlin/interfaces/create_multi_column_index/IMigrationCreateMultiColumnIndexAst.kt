package gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationCreateMultiColumnIndexAst : IMigrationAst {
    var tableName: String?
    var indexName: String?
    var columnNames: List<String>?
}
