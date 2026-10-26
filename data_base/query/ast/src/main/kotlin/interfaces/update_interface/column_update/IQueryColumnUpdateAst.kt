package gog.my_project.data_base.query.ast.interfaces.update_interface.column_update
import gog.my_project.data_base.query.ast.metadata.update.column_update.QueryColumnUpdateAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

interface IQueryColumnUpdateAst : IQueryAst<QueryColumnUpdateAstMetadata>  {
    var parameterAst: IQueryParameterAst<*>?
}
