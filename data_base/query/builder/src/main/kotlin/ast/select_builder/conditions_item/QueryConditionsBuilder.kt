package gog.my_project.data_base.query.builder.ast.select_builder.conditions_item

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item.IQueryConditionsApi
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.api.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item_collection.QueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.conditions_item_collection.QueryConditionsCollectionBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.builder.ast.parameter.QueryParameterBuilder

class QueryConditionsBuilder(
    override var ast: IQueryConditionsAst = QueryConditionsAst(),
): IQueryConditionsApi {

    private var parameterBindingScope = ParameterBindingScope()
    private var modelAliasContext = ModelTableAliasContext()
    private var querySourceContext = CteSourceContext()

    internal constructor(
        ast: IQueryConditionsAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this( ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    internal constructor(
        ast: IQueryConditionsAst,
        parameterBindingScope: ParameterBindingScope,
        modelAliasContext: ModelTableAliasContext,
        querySourceContext: CteSourceContext,
    ) : this(ast, parameterBindingScope) {
        this.modelAliasContext = modelAliasContext
        this.querySourceContext = querySourceContext
    }

    /* ==============================================================
    structure [logical]
    ============================================================== */

    override fun logical(logical: SqlLogical): IQueryConditionsApi {
        this.ast.metadata.conditionLogical = logical.value;
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
        QueryColumnsBaseBuilder(
            ast,
            modelAliasContext,
            querySourceContext,
        ).apply(blockColumn)
        this.ast.conditionSideLeft = ast;
        return this;
    }



    /* ==============================================================
    structure [operation]
    ============================================================== */

    override fun operation(operation: SqlConditionOperation): IQueryConditionsApi {
        this.ast.metadata.conditionOperation = operation.value;
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

    // ---------------------------------------------------------------------
    // LEGACY SIDE VALUE COLUMN
    // ---------------------------------------------------------------------
    override fun sideValueColumn(
        blockColumn: IQueryColumnsBaseApi.() -> Unit,
    ): IQueryConditionsApi {
        val columnAst = QueryColumnsBaseAst()
        QueryColumnsBaseBuilder(
            columnAst,
            modelAliasContext,
            querySourceContext,
        ).apply(blockColumn)
        ast.conditionSideRight = columnAst
        return this
    }


    // ---------------------------------------------------------------------
    // LEGACY SIDE VALUE PARAM
    // ---------------------------------------------------------------------
    override fun sideValueParam(
        blockParameter: IQueryParameterApi<Any?>.() -> Unit,
    ): IQueryConditionsApi {
        val parameterAst = QueryParameterAst<Any?>()
        val parameterBuilder = QueryParameterBuilder(parameterAst, parameterBindingScope::nextParameterName)
        parameterBuilder.apply(blockParameter)
        parameterBuilder.validate()
        val canonicalParameter = parameterBindingScope.canonical(parameterAst)
        // ---------------------------------------------------------------------
        // NEW PARAMETER AST IMPLEMENTATION
        // The AST node owns this definition; it is not mirrored into the legacy params list.
        // ---------------------------------------------------------------------
        ast.conditionSideRight = canonicalParameter
        return this
    }


    // ---------------------------------------------------------------------
    // NEW PARAMETER AST IMPLEMENTATION
    // Collection definitions are stored as ordered AST entries; old values stay on the legacy path.
    // ---------------------------------------------------------------------
    override fun sideValueCollection(
        blockParamsCollection: IQueryConditionsCollectionApi.() -> Unit,
    ): IQueryConditionsApi {
        val collectionAst = QueryConditionsCollectionAst()
        QueryConditionsCollectionBuilder(
            collectionAst,
            null,
            parameterBindingScope,
        ).apply(blockParamsCollection)
        ast.conditionSideRight = collectionAst
        return this
    }



}
