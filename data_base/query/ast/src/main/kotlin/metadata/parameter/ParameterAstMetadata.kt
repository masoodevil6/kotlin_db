package gog.my_project.data_base.query.ast.metadata.parameter

import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata

/**
 * Authoritative parameter values exposed to the meta_data module for QueryParameterAst.
 */
class QueryParameterAstMetadata<T> : IQueryAstMetadata {
    var name: String? = null
    var value: T? = null
    var sqlType: Int? = null
}
