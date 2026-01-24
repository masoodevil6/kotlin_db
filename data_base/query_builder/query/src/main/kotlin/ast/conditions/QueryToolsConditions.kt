package gog.my_project.data_base.query_builder.query.ast.conditions

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.column.QueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.ast.conditions.condition_in.QueryToolsConditionsCollection
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditions
import gog.my_project.data_base.query_builder.query.interfaces.conditions.condition_in.IQueryToolsConditionsCollection
import gog.my_project.data_base.query_builder.query.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query_builder.query.tools.enums.SqlLogical


class QueryToolsConditions(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsConditions
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







    /* ==============================================================
    structure
    ============================================================== */
    override fun setIsAddLogical(isAddLogical: Boolean){
        this.isAddLogical = isAddLogical;
    }




    override fun logical(logical: SqlLogical): IQueryToolsConditions {
        this.conditionLogical = logical;
        return this;
    }

    override fun logicalAnd(): IQueryToolsConditions {
        return this.logical(SqlLogical.And)
    }

    override fun logicalOr(): IQueryToolsConditions {
        return this.logical(SqlLogical.Or)
    }

    override fun logicalOn(): IQueryToolsConditions {
        return this.logical(SqlLogical.On)
    }




    override fun sideSelector(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions {
        val builder = QueryToolsColumnsBase(params);
        val query = builder.blockColumn();
        this.conditionSideLeft = query
        return this;
    }





    override fun operation(operation: SqlConditionOperation): IQueryToolsConditions {
        this.conditionOperation = operation;
        return this;
    }

    override fun operationEqual(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.Equals);
    }

    override fun operationNotEqual(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.NotEqual);
    }

    override fun operationGreaterThan(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.GreaterThan);
    }

    override fun operationGreaterThanOrEqual(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.GreaterThanOrEqual);
    }

    override fun operationLessThen(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.LessThan);
    }

    override fun operationLessThenOrEqual(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.LessThanOrEqual);
    }

    override fun operationLike(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.Like);
    }

    override fun operationNotLike(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.NotLike);
    }

    override fun operationIn(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.In);
    }

    override fun operationNotIn(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.NotIn);
    }

    override fun operationBetween(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.Between);
    }

    override fun operationNotBetween(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.NotBetween);
    }

    override fun operationIs(): IQueryToolsConditions {
        this.operation(SqlConditionOperation.Is);
        return this;
    }

    override fun operationIsNot(): IQueryToolsConditions {
        this.operation(SqlConditionOperation.IsNot);
        return this;
    }

    override fun operationContains(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.Contains);
    }









    override fun sideValue(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions {
        val builder = QueryToolsColumnsBase(params);
        val query = builder.blockColumn();
        this.conditionSideRight = query
        return this;
    }




    override fun <T> sideValue(
        paramName: String ,
        paramValue: T
    ): IQueryToolsConditions {
        this.conditionSideRight = ":${paramName}";
        params += SqlParameter.of(paramName , paramValue)
        return this;
    }



    override fun  sideValueCollection(
        paramName: String,
        blockParamsCollection: IQueryToolsConditionsCollection.() -> IQueryToolsConditionsCollection
    ): IQueryToolsConditions {
        val paramsCollection = QueryToolsConditionsCollection(params , paramName)
        this.conditionSideRight = blockParamsCollection(paramsCollection)
        return this;
    }




//    override fun sideValue(
//        paramName: String,
//        blockParamsLike: IQueryToolsConditionLike.() -> IQueryToolsConditionLike
//    ): IQueryToolsConditions {
//        val paramsIn = QueryToolsConditionLike(params, paramName)
//        this.conditionSideRight = blockParamsLike(paramsIn)
//        return this;
//    }


}