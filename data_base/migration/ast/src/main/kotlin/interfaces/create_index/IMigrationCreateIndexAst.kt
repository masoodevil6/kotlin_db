package gog.my_project.data_base.migration.ast.interfaces.create_index

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationCreateIndexAst : IMigrationAst {
    var tableName: String?
    var indexName: String?
    var columnName: String?
    var indexMethod: IndexMethod?
}
