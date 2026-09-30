package gog.my_project.data_base.query.ast.schema.select_schema.joins

import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst

class QueryJoinsAst : IQueryJoinsAst {

    override var joins: MutableList<IQueryJoinsItemAst> = mutableListOf();

}