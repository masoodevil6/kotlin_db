package gog.my_project.data_base.query.builder.interfaces.builder.conditions_item

import gog.my_project.data_base.query.api.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.builder.interfaces.builder.column_base.IQueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.conditions.IQueryConditionBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.conditions_item_collection.IQueryConditionsCollectionBuilder

interface IQueryConditionsBuilder : IQueryConditionBuilder {

    var ast: IQueryConditionsAst;

    fun logical(logical: SqlLogical): IQueryConditionsBuilder;
    fun logicalAnd(): IQueryConditionsBuilder;
    fun logicalOr(): IQueryConditionsBuilder;
    fun logicalOn(): IQueryConditionsBuilder;

    fun sideSelector(blockColumn: IQueryColumnsBaseBuilder.() -> Unit): IQueryConditionsBuilder

    fun operation(operation: SqlConditionOperation): IQueryConditionsBuilder;
    fun operationEqual(): IQueryConditionsBuilder;
    fun operationNotEqual(): IQueryConditionsBuilder;
    fun operationGreaterThan(): IQueryConditionsBuilder;
    fun operationGreaterThanOrEqual(): IQueryConditionsBuilder;
    fun operationLessThen(): IQueryConditionsBuilder;
    fun operationLessThenOrEqual(): IQueryConditionsBuilder;
    fun operationLike(): IQueryConditionsBuilder;
    fun operationNotLike(): IQueryConditionsBuilder;
    fun operationIn(): IQueryConditionsBuilder;
    fun operationNotIn(): IQueryConditionsBuilder;
    fun operationBetween(): IQueryConditionsBuilder;
    fun operationNotBetween(): IQueryConditionsBuilder;
    fun operationIs(): IQueryConditionsBuilder;
    fun operationIsNot(): IQueryConditionsBuilder;
    fun operationContains(): IQueryConditionsBuilder;

    fun sideValue(blockColumn: IQueryColumnsBaseBuilder.() -> Unit): IQueryConditionsBuilder

    //fun <T> sideValue( paramValue: T): IQueryConditionsBuilder
    fun <T> sideValue(paramName: String , paramValue: T): IQueryConditionsBuilder

    //    fun sideValueCollection( blockParamsCollection: IQueryConditionsCollectionBuilder.() -> Unit): IQueryConditionsBuilder
    fun sideValueCollection(paramName: String , blockParamsCollection: IQueryConditionsCollectionBuilder.() -> Unit): IQueryConditionsBuilder


}