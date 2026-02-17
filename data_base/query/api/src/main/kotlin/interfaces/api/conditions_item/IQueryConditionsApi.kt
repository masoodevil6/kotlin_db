package gog.my_project.data_base.query.api.interfaces.api.conditions_item

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.api.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.condition_item.IQueryConditionsAst

interface IQueryConditionsApi : IQueryApi {

    var ast: IQueryConditionsAst;

    fun logical(logical: SqlLogical): IQueryConditionsApi;
    fun logicalAnd(): IQueryConditionsApi;
    fun logicalOr(): IQueryConditionsApi;
    fun logicalOn(): IQueryConditionsApi;

    fun sideSelector(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryConditionsApi

    fun operation(operation: SqlConditionOperation): IQueryConditionsApi;
    fun operationEqual(): IQueryConditionsApi;
    fun operationNotEqual(): IQueryConditionsApi;
    fun operationGreaterThan(): IQueryConditionsApi;
    fun operationGreaterThanOrEqual(): IQueryConditionsApi;
    fun operationLessThen(): IQueryConditionsApi;
    fun operationLessThenOrEqual(): IQueryConditionsApi;
    fun operationLike(): IQueryConditionsApi;
    fun operationNotLike(): IQueryConditionsApi;
    fun operationIn(): IQueryConditionsApi;
    fun operationNotIn(): IQueryConditionsApi;
    fun operationBetween(): IQueryConditionsApi;
    fun operationNotBetween(): IQueryConditionsApi;
    fun operationIs(): IQueryConditionsApi;
    fun operationIsNot(): IQueryConditionsApi;
    fun operationContains(): IQueryConditionsApi;

    fun sideValue(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryConditionsApi

    //fun <T> sideValue( paramValue: T): IQueryConditionsApi
    fun <T> sideValue(paramName: String , paramValue: T): IQueryConditionsApi

    //    fun sideValueCollection( blockParamsCollection: IQueryConditionCollectionApi.() -> Unit): IQueryConditionsApi
    fun sideValueCollection(paramName: String , blockParamsCollection: IQueryConditionsCollectionApi.() -> Unit): IQueryConditionsApi


}