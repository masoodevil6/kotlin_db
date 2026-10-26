package gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item_collection

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst

interface IQueryConditionsCollectionApi : IQueryApi<IQueryConditionsCollectionAst> {

    /** Adds one independent AST parameter definition to the collection. */
    fun addParam(blockParameter: IQueryParameterApi<Any?>.() -> Unit): IQueryConditionsCollectionApi

}
