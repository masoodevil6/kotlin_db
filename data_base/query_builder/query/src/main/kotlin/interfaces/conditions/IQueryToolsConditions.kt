package gog.my_project.data_base.query_builder.query.interfaces.conditions

import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.conditions.condition_in.IQueryToolsConditionsCollection
import gog.my_project.data_base.query_builder.query.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query_builder.query.tools.enums.SqlLogical

interface IQueryToolsConditions : IQueryToolsIsConditions {

    fun getConditionLogical(): SqlLogical?;
    fun getConditionSideLeft(): IQueryToolsColumnsBase?;
    fun getConditionOperation(): SqlConditionOperation?;
    fun getConditionSideRight(): Any?;

    fun logical(logical: SqlLogical): IQueryToolsConditions;
    fun logicalAnd(): IQueryToolsConditions;
    fun logicalOr(): IQueryToolsConditions;
    fun logicalOn(): IQueryToolsConditions;

    fun sideSelector(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions

    fun operation(operation: SqlConditionOperation): IQueryToolsConditions;
    fun operationEqual(): IQueryToolsConditions;
    fun operationNotEqual(): IQueryToolsConditions;
    fun operationGreaterThan(): IQueryToolsConditions;
    fun operationGreaterThanOrEqual(): IQueryToolsConditions;
    fun operationLessThen(): IQueryToolsConditions;
    fun operationLessThenOrEqual(): IQueryToolsConditions;
    fun operationLike(): IQueryToolsConditions;
    fun operationNotLike(): IQueryToolsConditions;
    fun operationIn(): IQueryToolsConditions;
    fun operationNotIn(): IQueryToolsConditions;
    fun operationBetween(): IQueryToolsConditions;
    fun operationNotBetween(): IQueryToolsConditions;
    fun operationIs(): IQueryToolsConditions;
    fun operationIsNot(): IQueryToolsConditions;
    fun operationContains(): IQueryToolsConditions;

    fun sideValue(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions

    //fun <T> sideValue( paramValue: T): IQueryToolsConditions
    fun <T> sideValue(paramName: String , paramValue: T): IQueryToolsConditions

    //    fun sideValueCollection( blockParamsCollection: IQueryToolsConditionsCollection.() -> IQueryToolsConditionsCollection): IQueryToolsConditions
    fun sideValueCollection(paramName: String , blockParamsCollection: IQueryToolsConditionsCollection.() -> IQueryToolsConditionsCollection): IQueryToolsConditions

}