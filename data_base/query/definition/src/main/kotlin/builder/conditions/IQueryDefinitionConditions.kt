package gog.my_project.data_base.query.definition.interfaces.conditions

import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase
import gog.my_project.data_base.query.definition.interfaces.conditions.condition_special.IQueryDefinitionConditionCollection
import gog.my_project.data_base.query.definition.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.definition.tools.enums.SqlLogical

interface IQueryDefinitionConditions {

    fun logical(logical: SqlLogical): IQueryDefinitionConditions;
    fun logicalAnd(): IQueryDefinitionConditions;
    fun logicalOr(): IQueryDefinitionConditions;
    fun logicalOn(): IQueryDefinitionConditions;

    fun sideSelector(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionConditions

    fun operation(operation: SqlConditionOperation): IQueryDefinitionConditions;
    fun operationEqual(): IQueryDefinitionConditions;
    fun operationNotEqual(): IQueryDefinitionConditions;
    fun operationGreaterThan(): IQueryDefinitionConditions;
    fun operationGreaterThanOrEqual(): IQueryDefinitionConditions;
    fun operationLessThen(): IQueryDefinitionConditions;
    fun operationLessThenOrEqual(): IQueryDefinitionConditions;
    fun operationLike(): IQueryDefinitionConditions;
    fun operationNotLike(): IQueryDefinitionConditions;
    fun operationIn(): IQueryDefinitionConditions;
    fun operationNotIn(): IQueryDefinitionConditions;
    fun operationBetween(): IQueryDefinitionConditions;
    fun operationNotBetween(): IQueryDefinitionConditions;
    fun operationIs(): IQueryDefinitionConditions;
    fun operationIsNot(): IQueryDefinitionConditions;
    fun operationContains(): IQueryDefinitionConditions;

    fun sideValue(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionConditions

    //fun <T> sideValue( paramValue: T): QueryDefinitionConditions
    fun <T> sideValue(paramName: String , paramValue: T): IQueryDefinitionConditions

    //    fun sideValueCollection( blockParamsCollection: IQueryDefinitionConditionCollection.() -> IQueryDefinitionConditionCollection): QueryDefinitionConditions
    fun sideValueCollection(paramName: String , blockParamsCollection: IQueryDefinitionConditionCollection.() -> IQueryDefinitionConditionCollection): IQueryDefinitionConditions


}