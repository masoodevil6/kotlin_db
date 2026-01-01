package gog.my_project.query.query_builder.tools.conditions

import gog.my_project.enums.SqlLogical
import gog.my_project.enums.SqlConditionOperation
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditions
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder
import gog.my_project.query.query_builder.tools.column.QueryToolsColumnsBase


class QueryToolsConditions(
    private val sqlDialect: ISqlDialect
):
    IQueryToolsConditions
{


    private var isAddLogical: Boolean = false;
    private var conditionLogical: String? = null;
    private var conditionSideLeft: String? = null;
    private var conditionOperation: String? = null;
    private var conditionSideRight : String? = null;


    override fun getConditionLogical(): String? {
        return conditionLogical;
    }

    override fun getConditionSideLeft(): String? {
        return conditionSideLeft;
    }

    override fun getConditionOperation(): String? {
        return conditionOperation;
    }

    override fun getConditionSideRight(): String? {
        return conditionSideRight;
    }

    override fun isAddLogical(): Boolean {
        return isAddLogical;
    }






    override fun toSql(): String? {
        return sqlDialect.getConditionSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }










    override fun setIsAddLogical(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }








    override fun logical(logical: SqlLogical): IQueryToolsConditions {
        this.conditionLogical = logical.value;
        this.conditionSideLeft = "";
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







    override fun sideLeft(sideLeft: String): IQueryToolsConditions {
        this.conditionSideLeft = sideLeft;
        this.conditionOperation = "";
        return this;
    }

    override fun sideLeft(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions {
        val builder = QueryToolsColumnsBase(sqlDialect);
        val query = builder.blockColumn().toSql();
        if (query != null){
            return sideLeft(query);
        }
        return this;
    }

    override fun sideLeftQuery(blockQuery: IQueryBuilder.() -> IQueryBuilder): IQueryToolsConditions {
        val builder = QueryBuilder(sqlDialect);
        val query = builder.blockQuery().toSql();
        if (query != null){
            return sideLeft("($query)");
        }
        return this;
    }







    override fun operation(operation: SqlConditionOperation): IQueryToolsConditions {
        this.conditionOperation = operation.value;
        this.conditionSideRight = "";
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
        return this.operation(SqlConditionOperation.IsNull);
    }

    override fun operationIsNotNull(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.IsNotNull);
    }

    override fun operationContains(): IQueryToolsConditions {
        return this.operation(SqlConditionOperation.Contains);
    }










    override fun sideRight(sideRight: String): IQueryToolsConditions {
        this.conditionSideRight = sideRight;
        return this;
    }

    override fun sideRight(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsConditions {
        val builder = QueryToolsColumnsBase(sqlDialect);
        val query = builder.blockColumn().toSql();
        if (query != null){
            return sideRight(query);
        }
        return this;
    }

    override fun sideRightQuery(blockQuery: IQueryBuilder.() -> IQueryBuilder): IQueryToolsConditions {
        val builder = QueryBuilder(sqlDialect);
        val query = builder.blockQuery().toSql();
        if (query != null){
            this.conditionSideRight = "($query)";
        }
        return this;
    }


}