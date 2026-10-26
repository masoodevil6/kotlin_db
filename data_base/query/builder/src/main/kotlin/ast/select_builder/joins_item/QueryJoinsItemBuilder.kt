package gog.my_project.data_base.query.builder.ast.select_builder.joins_item

import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.tools.enums.SqlTypeJoin
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.builder.ast.select_builder.conditions_group.QueryConditionsGroupsBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext

class QueryJoinsItemBuilder(
    override var ast: IQueryJoinsItemAst = QueryJoinsItemAst(),
)  : IQueryJoinsItemApi {

    private var parameterBindingScope = ParameterBindingScope()
    private var modelAliasContext = ModelTableAliasContext()
    private var querySourceContext = CteSourceContext()

    internal constructor(
        ast: IQueryJoinsItemAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    internal constructor(
        ast: IQueryJoinsItemAst,
        parameterBindingScope: ParameterBindingScope,
        modelAliasContext: ModelTableAliasContext,
    ) : this(ast, parameterBindingScope) {
        this.modelAliasContext = modelAliasContext
    }

    internal constructor(
        ast: IQueryJoinsItemAst,
        parameterBindingScope: ParameterBindingScope,
        modelAliasContext: ModelTableAliasContext,
        querySourceContext: CteSourceContext,
    ) : this(ast, parameterBindingScope, modelAliasContext) {
        this.querySourceContext = querySourceContext
    }



    /* ==============================================================
    structure [join]
    ============================================================== */
    override fun typeJoin(type: SqlTypeJoin): IQueryJoinsItemApi {
        this.ast.metadata.joinType = type.value;
        return this;
    }

    override fun innerJoin(): IQueryJoinsItemApi {
        return this.typeJoin(SqlTypeJoin.InnerJoin);
    }

    override fun leftJoin(): IQueryJoinsItemApi {
        return this.typeJoin(SqlTypeJoin.LeftJoin);
    }

    override fun rightJoin(): IQueryJoinsItemApi {
        return this.typeJoin(SqlTypeJoin.RightJoin);
    }





    /* ==============================================================
    structure [table]
    ============================================================== */
    override fun table(
        blockTable: IQueryTableApi.() -> Unit
    ): IQueryJoinsItemApi
    {
        val ast = QueryTableAst();
        QueryTableBuilder(
            ast,
            modelAliasContext,
            parameterBindingScope = parameterBindingScope,
        ).apply(blockTable)
        this.ast.joinTable = ast;
        return this;
    }




    /* ==============================================================
    structure [condition]
    ============================================================== */
    override fun condition(
        blockCondition: IQueryConditionsGroupsApi.() -> Unit
    ): IQueryJoinsItemApi
    {
        val ast = QueryConditionsGroupsAst();
        QueryConditionsGroupsBuilder(
            ast,
            parameterBindingScope,
            modelAliasContext,
            querySourceContext,
        ).apply(blockCondition)
        this.ast.joinConditions = ast;
        return this;
    }

}
