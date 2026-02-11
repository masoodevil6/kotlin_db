package gog.my_project.data_base.query.ast.schema.select

import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst

class QuerySelectAst : IQuerySelectAst {

    override var columns: MutableList<IQueryColumnsAst> = mutableListOf()

}