package gog.my_project.data_base.query.api.interfaces.withs_item

import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst

interface IQueryWithsItemApi : IQueryApi {

    var ast: IQueryWithsItemAst;

    fun with(with : ICte): IQueryWithsItemApi;

}