package gog.my_project.data_base.query.builder.interfaces.builder.withs_item

import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery

interface IQueryWithItemBuilder : IQuery {

    var ast: IQueryWithsItemAst;

    fun with(with : ICte): IQueryWithItemBuilder;

}