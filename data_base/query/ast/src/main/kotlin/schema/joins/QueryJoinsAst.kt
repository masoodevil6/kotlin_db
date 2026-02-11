package gog.my_project.data_base.query.ast.schema.joins

import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst

class QueryJoinsAst : IQueryJoinsAst {

    override var joins: MutableList<IQueryJoinsItemAst> = mutableListOf();

}