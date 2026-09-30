package gog.my_project.data_base.migration.ast.interfaces.drop_index

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationDropIndexAst : IMigrationAst {
    var tableName: String?
    var indexName: String?
}
