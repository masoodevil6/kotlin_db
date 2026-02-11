package gog.my_project.data_base.query.builder.interfaces.withs_item

import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.builder.interfaces.IQuery

interface IQueryWithItemBuilder : IQuery {

    var ast: IQueryWithsItemAst;

    fun with(with : ICte): IQueryWithItemBuilder;

}