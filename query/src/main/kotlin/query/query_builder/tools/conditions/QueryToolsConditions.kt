package gog.my_project.query.query_builder.tools.conditions

import gog.my_project.datas.SqlParameter
import gog.my_project.enums.SqlLogical
import gog.my_project.enums.SqlConditionOperation
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditions
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.column.QueryToolsColumnsBase


class QueryToolsConditions(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsConditions
{



    private var isAddLogical: Boolean = false;



    private var conditionLogical:    SqlLogical? = SqlLogical.And;
    private var conditionSideLeft:   IQueryToolsColumnsBase? = null;
    private var conditionOperation:  SqlConditionOperation? = null;
    private var conditionSideRight : IQueryToolsColumnsBase? = null;







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

    override fun getConditionSideRight(): IQueryToolsColumnsBase? {
        return conditionSideRight;
    }

    override fun isAddLogical(): Boolean {
        return isAddLogical;
    }






    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getConditionSql(this);
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




    override fun sideLeft(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions {
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

    override fun operationIsNull(): IQueryToolsConditions {
        this.operation(SqlConditionOperation.IsNull);
        this.sideRight {
            columnName("")
        }
        return this;
    }

    override fun operationIsNotNull(): IQueryToolsConditions {
        this.operation(SqlConditionOperation.IsNotNull);
        this.sideRight {
            columnName("")
        }
        return this;
    }

    override fun operationContains(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.Contains);
    }









    override fun sideRight(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions {
        val builder = QueryToolsColumnsBase(params);
        val query = builder.blockColumn();
        this.conditionSideRight = query
        return this;
    }

    override fun <T> sideRightValue(paramName: String , paramValue: T): IQueryToolsConditions {
        this.conditionSideRight = QueryToolsColumnsBase(params).columnName(":${paramName}");
        params += SqlParameter.of(paramName , paramValue)
        return this;
    }


}