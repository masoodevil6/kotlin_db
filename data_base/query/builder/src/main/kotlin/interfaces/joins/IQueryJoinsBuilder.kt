package gog.my_project.data_base.query.builder.interfaces.joins

import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.builder.interfaces.IQuery
import gog.my_project.data_base.query.builder.interfaces.joins_item.IQueryJoinsItemBuilder

interface IQueryJoinsBuilder : IQuery {

    var ast: IQueryJoinsAst;

    fun addJoin(blockJoin: IQueryJoinsItemBuilder.()-> Unit): IQueryJoinsBuilder;

}