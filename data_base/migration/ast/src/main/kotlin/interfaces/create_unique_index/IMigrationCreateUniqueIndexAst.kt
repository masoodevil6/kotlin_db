package gog.my_project.data_base.migration.ast.interfaces.create_unique_index

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationCreateUniqueIndexAst : IMigrationAst {
    var tableName: String?
    var indexName: String?
    var columnName: String?
}
