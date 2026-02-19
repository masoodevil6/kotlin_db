package gog.my_project.data_base.query.ast.schema.select_schema.select

import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst


class QuerySelectAst : IQuerySelectAst {

    override var columns: MutableList<IQueryColumnsAst> = mutableListOf()

}