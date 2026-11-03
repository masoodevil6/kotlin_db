package gog.my_project.data_base_query.ast.metadata.select.option_limit

import gog.my_project.data_base_query.ast.metadata.interfaces.IQueryAstMetadata

class QueryOptionLimitAstMetadata : IQueryAstMetadata {
    var pageLimit: Long? = null
}
