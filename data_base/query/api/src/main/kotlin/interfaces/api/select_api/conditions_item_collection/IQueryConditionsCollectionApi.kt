package gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item_collection

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst

interface IQueryConditionsCollectionApi : IQueryApi {

    var ast: IQueryConditionsCollectionAst;

    fun <T> addParam(paramValue: T) : IQueryConditionsCollectionApi;

}