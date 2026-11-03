package gog.my_project.data_base_query.ast.metadata.update.column_update

import gog.my_project.data_base_query.ast.metadata.interfaces.IQueryAstMetadata

class QueryColumnUpdateAstMetadata : IQueryAstMetadata {
    var columnAlias: String? = null
    var columnName: String? = null
    var columnTag: String? = null
}
