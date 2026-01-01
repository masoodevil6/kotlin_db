package gog.my_project.query.query_builder.tools.where

import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.conditions.QueryToolsConditionsGroups
import gog.my_project.tools.templates.OTemplateSqlDialect


class QueryToolsWhere(
    private val sqlDialect: ISqlDialect
) : IQueryToolsWhere {



    var condition: IQueryToolsConditionsGroups? = null;
    override fun getGroupCondition(): IQueryToolsConditionsGroups? {
        return condition;
    }


    override fun whereSetup(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsWhere {
        val builder = QueryToolsConditionsGroups(sqlDialect);
        condition = builder.blockGroup();
        return this;
    }





    override fun toSql(): String? {
        return sqlDialect.getWhereSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryWhere= toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_WHERES, queryWhere ?: "");
    }


}