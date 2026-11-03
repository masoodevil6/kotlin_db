package gog.my_project.data_base_query.ast.metadata.select.option_offset

import gog.my_project.data_base_query.ast.metadata.interfaces.IQueryAstMetadata

class QueryOptionOffsetAstMetadata : IQueryAstMetadata {
    var pageOffset: Long? = null
}
