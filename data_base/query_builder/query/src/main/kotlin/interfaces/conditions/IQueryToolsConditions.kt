package gog.my_project.data_base.query_builder.query.interfaces.conditions

import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query_builder.query.tools.enums.SqlLogical

interface IQueryToolsConditions : IQueryToolsIsConditions {

    fun getConditionLogical(): SqlLogical?;
    fun getConditionSideLeft(): IQueryToolsColumnsBase?;
    fun getConditionOperation(): SqlConditionOperation?;
    fun getConditionSideRight(): IQueryToolsColumnsBase?;

    fun logical(logical: SqlLogical): IQueryToolsConditions;
    fun logicalAnd(): IQueryToolsConditions;
    fun logicalOr(): IQueryToolsConditions;
    fun logicalOn(): IQueryToolsConditions;

    fun sideLeft(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions

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
    fun operationIsNull(): IQueryToolsConditions;
    fun operationIsNotNull(): IQueryToolsConditions;
    fun operationContains(): IQueryToolsConditions;

    fun sideRight(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions
    fun <T> sideRightValue(paramName: String , paramValue: T): IQueryToolsConditions
}