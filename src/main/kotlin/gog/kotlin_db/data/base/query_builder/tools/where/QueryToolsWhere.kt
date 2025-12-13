package gog.kotlin_db.data.base.query_builder.tools.where


import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups.QueryToolsConditionsGroups
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates


class QueryToolsWhere(

) : IQueryToolsWhere{



    var condition: QueryToolsConditionsGroups? = null;

    override fun whereSetup(blockGroup: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsWhere {
        condition = blockGroup(QueryToolsConditionsGroups(QueryToolsConditionsGroups._LOGICAL_AND))
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