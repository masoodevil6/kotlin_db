package gog.my_project.data_base.query_builder.query.ast.where

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.conditions.QueryToolsConditionsGroups
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query_builder.query.interfaces.where.IQueryToolsWhere


class QueryToolsWhere(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsWhere
{


    var condition: IQueryToolsConditionsGroups? = null;

    /* ==============================================================
    template
    ============================================================== */
    override fun getGroupCondition(): IQueryToolsConditionsGroups? {
        return condition;
    }



    /* ==============================================================
    Builder
    ============================================================== */
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getWhereSql(this);
    }*/



    /* ==============================================================
    structure
    ============================================================== */
    override fun conditions(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsWhere {
        val builder = QueryToolsConditionsGroups(params);
        condition = builder.blockCondition();
        return this;
    }




}