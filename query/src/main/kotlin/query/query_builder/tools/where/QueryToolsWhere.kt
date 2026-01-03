package gog.my_project.query.query_builder.tools.where

import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.conditions.QueryToolsConditionsGroups

class QueryToolsWhere(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
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
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getWhereSql(this);
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun whereSetup(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsWhere {
        val builder = QueryToolsConditionsGroups();
        condition = builder.blockGroup();
        return this;
    }





}