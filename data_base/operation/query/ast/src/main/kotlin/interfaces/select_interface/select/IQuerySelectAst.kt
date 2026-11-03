package gog.my_project.data_base_query.ast.interfaces.select_interface.select
import gog.my_project.data_base_query.ast.metadata.select.select.QuerySelectAstMetadata

import gog.my_project.data_base_query.ast.interfaces.IQueryAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.column.IQueryColumnsAst

interface IQuerySelectAst  : IQueryAst<QuerySelectAstMetadata>  {

    var columns: MutableList<IQueryColumnsAst>;

}