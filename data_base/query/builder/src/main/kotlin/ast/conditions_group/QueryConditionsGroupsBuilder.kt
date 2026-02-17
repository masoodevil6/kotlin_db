package gog.my_project.data_base.query.builder.ast.conditions_group

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.api.conditions_item.IQueryConditionsApi
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.builder.ast.conditions_item.QueryConditionsBuilder

class QueryConditionsGroupsBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>() ,
    override var ast: IQueryConditionsGroupsAst = QueryConditionsGroupsAst(),
): IQueryConditionsGroupsApi {


    /* ==============================================================
    structure [logical]
    ============================================================== */

    override fun logical(
        logical: SqlLogical
    ): IQueryConditionsGroupsApi
    {
        this.ast.conditionLogical = logical.value;
        return this;
    }

    override fun logicalAnd(): IQueryConditionsGroupsApi {
        return this.logical(SqlLogical.And)
    }

    override fun logicalOr(): IQueryConditionsGroupsApi {
        return this.logical(SqlLogical.Or)
    }

    override fun logicalOn(): IQueryConditionsGroupsApi {
        return this.logical(SqlLogical.On)
    }



    /* ==============================================================
    structure [logical]
    ============================================================== */
    override fun addGroup(
        blockGroup: IQueryConditionsGroupsApi.() -> Unit
    ): IQueryConditionsGroupsApi
    {
        val ast = QueryConditionsGroupsAst();
        QueryConditionsGroupsBuilder(params ,ast  ).apply(blockGroup)
        this.ast.conditions.add(ast);
        return this;
    }

    override fun addCondition(
        blockCondition: IQueryConditionsApi.() -> Unit
    ): IQueryConditionsGroupsApi
    {
        var ast = QueryConditionsAst();
        QueryConditionsBuilder(params ,ast).apply(blockCondition)
        this.ast.conditions.add(ast);
        return this;
    }

}