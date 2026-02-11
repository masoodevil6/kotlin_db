package gog.my_project.data_base.query.builder.interfaces.withs

import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst
import gog.my_project.data_base.query.builder.interfaces.IQuery
import gog.my_project.data_base.query.builder.interfaces.withs_item.IQueryWithItemBuilder

interface IQueryWithsBuilder : IQuery {

    var ast: IQueryWithsAst;

    fun addWith(blockWith: IQueryWithItemBuilder.() -> Unit) : IQueryWithsBuilder;

}