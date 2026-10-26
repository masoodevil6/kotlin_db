package gog.my_project.data_base.query.ast.interfaces.select_interface.select
import gog.my_project.data_base.query.ast.metadata.select.select.QuerySelectAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst

interface IQuerySelectAst  : IQueryAst<QuerySelectAstMetadata>  {

    var columns: MutableList<IQueryColumnsAst>;

}