package gog.my_project.data_base_query.ast.schema.parameter

import gog.my_project.data_base_query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base_query.ast.metadata.parameter.QueryParameterAstMetadata

class QueryParameterAst<T>(
    override var metadata: QueryParameterAstMetadata<T> = QueryParameterAstMetadata<T>()
) : IQueryParameterAst<T> {

}
