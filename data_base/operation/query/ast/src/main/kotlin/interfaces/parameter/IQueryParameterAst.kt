package gog.my_project.data_base_query.ast.interfaces.parameter

import gog.my_project.data_base_query.ast.metadata.parameter.QueryParameterAstMetadata
import gog.my_project.data_base_query.ast.interfaces.IQueryAst

interface IQueryParameterAst<T> : IQueryAst<QueryParameterAstMetadata<T>> {

}
