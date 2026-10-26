package gog.my_project.data_base.query.ast.interfaces.parameter

import gog.my_project.data_base.query.ast.metadata.parameter.QueryParameterAstMetadata
import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryParameterAst<T> : IQueryAst<QueryParameterAstMetadata<T>> {

}
