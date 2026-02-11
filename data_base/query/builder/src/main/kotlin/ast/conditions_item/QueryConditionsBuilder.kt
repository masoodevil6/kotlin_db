package gog.my_project.data_base.query.builder.ast.conditions_item

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.conditions_item.IQueryConditionsApi
import gog.my_project.data_base.query.api.interfaces.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.api.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.schema.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.ast.schema.conditions_item_collection.QueryConditionsCollectionAst
import gog.my_project.data_base.query.builder.ast.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.conditions_item_collection.QueryConditionsCollectionBuilder
import kotlin.collections.plusAssign

class QueryConditionsBuilder(
    override var ast: IQueryConditionsAst = QueryConditionsAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
): IQueryConditionsApi {

    /* ==============================================================
    structure [logical]
    ============================================================== */

    override fun logical(logical: SqlLogical): IQueryConditionsApi {
        this.ast.conditionLogical = logical.value;
        return this;
    }

    override fun logicalAnd(): IQueryConditionsApi {
        this.logical(SqlLogical.And);
        return this;
    }

    override fun logicalOr(): IQueryConditionsApi {
        this.logical(SqlLogical.Or);
        return this;
    }

    override fun logicalOn(): IQueryConditionsApi {
        this.logical(SqlLogical.On);
        return this;
    }





    /* ==============================================================
    structure [side left]
    ============================================================== */

    override fun sideSelector(
        blockColumn: IQueryColumnsBaseApi.() -> Unit
    ): IQueryConditionsApi
    {
        val ast = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(ast, params).apply(blockColumn)
        this.ast.conditionSideLeft = ast;
        return this;
    }



    /* ==============================================================
    structure [operation]
    ============================================================== */

    override fun operation(operation: SqlConditionOperation): IQueryConditionsApi {
        this.ast.conditionOperation = operation.value;
        return this;
    }

    override fun operationEqual(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.Equals);
    }

    override fun operationNotEqual(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.NotEqual);
    }

    override fun operationGreaterThan(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.GreaterThan);
    }

    override fun operationGreaterThanOrEqual(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.GreaterThanOrEqual);
    }

    override fun operationLessThen(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.LessThan);
    }

    override fun operationLessThenOrEqual(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.LessThanOrEqual);
    }

    override fun operationLike(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.Like);
    }

    override fun operationNotLike(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.NotLike);
    }

    override fun operationIn(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.In);
    }

    override fun operationNotIn(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.NotIn);
    }

    override fun operationBetween(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.Between);
    }

    override fun operationNotBetween(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.NotBetween);
    }

    override fun operationIs(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.Is);
    }

    override fun operationIsNot(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.IsNot);
    }

    override fun operationContains(): IQueryConditionsApi {
        return this.operation(SqlConditionOperation.Contains);
    }




    /* ==============================================================
    structure [side right]
    ============================================================== */

    override fun sideValue(
        blockColumn: IQueryColumnsBaseApi.() -> Unit
    ): IQueryConditionsApi
    {
        val ast = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(ast , params).apply(blockColumn)
        this.ast.conditionSideRight = ast;
        return this;
    }


    override fun <T> sideValue(
        paramName: String,
        paramValue: T
    ): IQueryConditionsApi
    {
        this.ast.conditionSideRight = ":${paramName}";
        params += SqlParameter.of(paramName , paramValue)
        return this;
    }


    override fun sideValueCollection(
        paramName: String,
        blockParamsCollection: IQueryConditionsCollectionApi.() -> Unit
    ): IQueryConditionsApi
    {
        val ast = QueryConditionsCollectionAst();
        QueryConditionsCollectionBuilder(ast, params, paramName).apply(blockParamsCollection)
        this.ast.conditionSideRight = ast
        return this;
    }


}