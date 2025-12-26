package gog.my_project.query.query_builder.tools.where

import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.query_builder.tools.conditions.QueryToolsConditionsGroups


class QueryToolsWhere(

) : IQueryToolsWhere {



    var condition: IQueryToolsConditionsGroups? = null;

    override fun whereSetup(blockGroup: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryToolsWhere {
        condition = blockGroup(QueryToolsConditionsGroups(QueryToolsConditionsGroups.Companion._LOGICAL_AND))
        return this;
    }





    override fun getBaseTempSql(): String? {
        return "";
    }


    override fun toSql(): String? {
        val conditionStr = condition?.toWhereSql();
        return DatabaseConfig.dbTypeName.getWhereSql(conditionStr);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryWhere= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_WHERES, queryWhere ?: "");
    }


}