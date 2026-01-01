package gog.my_project.query.interfaces.query_builders.tools.conditions

import gog.my_project.enums.SqlLogical
import gog.my_project.enums.SqlConditionOperation
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase

interface IQueryToolsConditions : IQueryToolsIsConditions {

    fun getConditionLogical(): String?;
    fun getConditionSideLeft(): String?;
    fun getConditionOperation(): String?;
    fun getConditionSideRight(): String?;

    fun logical(logical: SqlLogical): IQueryToolsConditions;
    fun logicalAnd(): IQueryToolsConditions;
    fun logicalOr(): IQueryToolsConditions;
    fun logicalOn(): IQueryToolsConditions;

    fun sideLeft(sideLeft : String): IQueryToolsConditions;
    fun sideLeft(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions
    fun sideLeftQuery(blockQuery: IQueryBuilder.() -> IQueryBuilder): IQueryToolsConditions

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

    fun sideRight(sideRight: String): IQueryToolsConditions;
    fun sideRight(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions
    fun sideRightQuery(blockQuery: IQueryBuilder.() -> IQueryBuilder): IQueryToolsConditions
}