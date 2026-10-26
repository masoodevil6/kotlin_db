package gog.my_project.data_base.query.ast.metadata.insert.column_insert

import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata

class QueryColumnInsertAstMetadata : IQueryAstMetadata {
    var columnName: String? = null
    var columnTag: String? = null
}
