package gog.my_project.data_base.query.builder.interfaces.builder.withs

import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import gog.my_project.data_base.query.builder.interfaces.builder.withs_item.IQueryWithItemBuilder

interface IQueryWithsBuilder : IQuery {

    var ast: IQueryWithsAst;

    fun addWith(blockWith: IQueryWithItemBuilder.() -> Unit) : IQueryWithsBuilder;

}