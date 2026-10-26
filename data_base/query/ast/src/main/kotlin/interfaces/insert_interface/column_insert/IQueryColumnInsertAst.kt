package gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert
import gog.my_project.data_base.query.ast.metadata.insert.column_insert.QueryColumnInsertAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

interface IQueryColumnInsertAst : IQueryAst<QueryColumnInsertAstMetadata> {
    var parameterAst: IQueryParameterAst<*>?
}
