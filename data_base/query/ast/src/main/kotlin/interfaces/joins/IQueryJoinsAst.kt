package gog.my_project.data_base.query.ast.interfaces.joins

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst

interface IQueryJoinsAst : IQueryAst {

    var joins: MutableList<IQueryJoinsItemAst>;

}