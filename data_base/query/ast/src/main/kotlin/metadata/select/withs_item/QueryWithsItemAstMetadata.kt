package gog.my_project.data_base.query.ast.metadata.select.withs_item

import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata

class QueryWithsItemAstMetadata : IQueryAstMetadata {
    var withName: String? = null

    /** Non-null only when this WITH item represents a composed Cte object. */
    var cteRuntimeName: String? = null
    var logicalQueryName: String? = null
}
