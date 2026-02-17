package gog.my_project.data_base.query.builder.ast.joins_item

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.api.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.api.interfaces.api.table.IQueryTableApi
import gog.my_project.data_base.query.api.tools.enums.SqlTypeJoin
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.ast.schema.table.QueryTableAst
import gog.my_project.data_base.query.builder.ast.conditions_group.QueryConditionsGroupsBuilder
import gog.my_project.data_base.query.builder.ast.table.QueryTableBuilder

class QueryJoinsItemBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryJoinsItemAst = QueryJoinsItemAst(),
)  : IQueryJoinsItemApi {



    /* ==============================================================
    structure [join]
    ============================================================== */
    override fun typeJoin(type: SqlTypeJoin): IQueryJoinsItemApi {
        this.ast.joinType = type.value;
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
        QueryTableBuilder(params ,ast).apply(blockTable)
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
        QueryConditionsGroupsBuilder(params , ast).apply(blockCondition)
        this.ast.joinConditions = ast;
        return this;
    }

}