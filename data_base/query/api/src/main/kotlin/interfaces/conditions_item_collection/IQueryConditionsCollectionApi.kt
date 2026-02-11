package gog.my_project.data_base.query.api.interfaces.conditions_item_collection

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.condition_item_collection.IQueryConditionsCollectionAst

interface IQueryConditionsCollectionApi : IQueryApi {

    var ast: IQueryConditionsCollectionAst;

    fun <T> addParam(paramValue: T) : IQueryConditionsCollectionApi;

}