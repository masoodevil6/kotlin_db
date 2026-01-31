package gog.my_project.data_base.query.builder.ast.conditions

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.column.QueryToolsColumnsBase
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditions
import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase
import gog.my_project.data_base.query.definition.interfaces.conditions.IQueryDefinitionConditions
import gog.my_project.data_base.query.definition.interfaces.conditions.condition_special.IQueryDefinitionConditionCollection
import gog.my_project.data_base.query.definition.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.definition.tools.enums.SqlLogical
import gog.my_project.data_base.query_builder.query.ast.conditions.condition_in.QueryToolsConditionsCollection

class QueryToolsConditions(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsConditions , IQueryDefinitionConditions
{



    private var isAddLogical: Boolean = false;



    private var conditionLogical:    SqlLogical? = SqlLogical.And;
    private var conditionSideLeft:   IQueryToolsColumnsBase? = null;
    private var conditionOperation:  SqlConditionOperation? = null;
    private var conditionSideRight : Any? = null;







    /* ==============================================================
    template
    ============================================================== */
    override fun getConditionLogical(): SqlLogical? {
        return conditionLogical;
    }

    override fun getConditionSideLeft(): IQueryToolsColumnsBase? {
        return conditionSideLeft;
    }

    override fun getConditionOperation(): SqlConditionOperation? {
        return conditionOperation;
    }

    override fun getConditionSideRight(): Any? {
        return conditionSideRight;
    }

    override fun isAddLogical(): Boolean {
        return isAddLogical;
    }







    override fun setIsAddLogical(isAddLogical: Boolean){
        this.isAddLogical = isAddLogical;
    }



    /* ==============================================================
    structure
    ============================================================== */

    override fun logical(logical: SqlLogical): IQueryDefinitionConditions {
        this.conditionLogical = logical;
        return this;
    }

    override fun logicalAnd(): IQueryDefinitionConditions {
        return this.logical(SqlLogical.And)
    }

    override fun logicalOr(): IQueryDefinitionConditions {
        return this.logical(SqlLogical.Or)
    }

    override fun logicalOn(): IQueryDefinitionConditions {
        return this.logical(SqlLogical.On)
    }




    override fun sideSelector(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionConditions {
        val builder = QueryToolsColumnsBase(params);
        val query = builder.blockColumn();
        this.conditionSideLeft = query as IQueryToolsColumnsBase?
        return this;
    }





    override fun operation(operation: SqlConditionOperation): IQueryDefinitionConditions {
        this.conditionOperation = operation;
        return this;
    }

    override fun operationEqual(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.Equals);
    }

    override fun operationNotEqual(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.NotEqual);
    }

    override fun operationGreaterThan(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.GreaterThan);
    }

    override fun operationGreaterThanOrEqual(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.GreaterThanOrEqual);
    }

    override fun operationLessThen(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.LessThan);
    }

    override fun operationLessThenOrEqual(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.LessThanOrEqual);
    }

    override fun operationLike(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.Like);
    }

    override fun operationNotLike(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.NotLike);
    }

    override fun operationIn(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.In);
    }

    override fun operationNotIn(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.NotIn);
    }

    override fun operationBetween(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.Between);
    }

    override fun operationNotBetween(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.NotBetween);
    }

    override fun operationIs(): IQueryDefinitionConditions {
        this.operation(SqlConditionOperation.Is);
        return this;
    }

    override fun operationIsNot(): IQueryDefinitionConditions {
        this.operation(SqlConditionOperation.IsNot);
        return this;
    }

    override fun operationContains(): IQueryDefinitionConditions {
        return this.operation(SqlConditionOperation.Contains);
    }









    override fun sideValue(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionConditions {
        val builder = QueryToolsColumnsBase(params);
        val query = builder.blockColumn();
        this.conditionSideRight = query
        return this;
    }




    override fun <T> sideValue(
        paramName: String ,
        paramValue: T
    ): IQueryDefinitionConditions {
        this.conditionSideRight = ":${paramName}";
        params += SqlParameter.of(paramName , paramValue)
        return this;
    }



    override fun  sideValueCollection(
        paramName: String,
        blockParamsCollection: IQueryDefinitionConditionCollection.() -> IQueryDefinitionConditionCollection
    ): IQueryDefinitionConditions {
        val paramsCollection = QueryToolsConditionsCollection(params , paramName)
        this.conditionSideRight = blockParamsCollection(paramsCollection)
        return this;
    }




//    override fun sideValue(
//        paramName: String,
//        blockParamsLike: IQueryToolsConditionLike.() -> IQueryToolsConditionLike
//    ): IQueryDefinitionConditions {
//        val paramsIn = QueryToolsConditionLike(params, paramName)
//        this.conditionSideRight = blockParamsLike(paramsIn)
//        return this;
//    }


}