package gog.my_project.data_base.query.ast.interfaces.select

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst

interface IQuerySelectAst  : IQueryAst  {

    var columns: MutableList<IQueryColumnsAst>;

}