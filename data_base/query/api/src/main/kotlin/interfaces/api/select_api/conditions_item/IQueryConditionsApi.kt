package gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.api.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst


interface IQueryConditionsApi : IQueryApi<IQueryConditionsAst> {

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

    /** Right-hand column expression. */
    fun sideValueColumn(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryConditionsApi

    /** Right-hand parameter AST definition; named separately to keep the column DSL unambiguous. */
    fun sideValueParam(blockParameter: IQueryParameterApi<Any?>.() -> Unit): IQueryConditionsApi

    /** Defines a collection using independent parameter AST nodes. */
    fun sideValueCollection(blockParamsCollection: IQueryConditionsCollectionApi.() -> Unit): IQueryConditionsApi

}
