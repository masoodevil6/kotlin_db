package gog.kotlin_db.data.base.QueryBuilder.tools.where

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder.Companion._LOGICAL_AND
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroup
import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options


class QueryTools_where(

) : IQueryTools_where{

    companion object {
        const val _TAG_TEMP_WHERES=                "{{_TAG_TEMP_WHERES}}"
        const val _TAG_TEMP_WHERES_CONDITIONS=     "{{_TAG_TEMP_WHERES_CONDITIONS}}"
    }




    var condition: QueryTools_conditionsGroup? = null;

    override fun where(blockGroup: (QueryTools_conditionsGroup) -> QueryTools_conditionsGroup): QueryTools_where {
        condition = blockGroup(QueryTools_conditionsGroup(_LOGICAL_AND))
        return this;
    }





    override fun getBaseTempSql(): String? {
        return " where $_TAG_TEMP_WHERES_CONDITIONS " ;
    }


    override fun toSql(): String? {
        val conditionStr = condition?.toWhereSql();
        if (conditionStr != null) {
            var queryTemp = getBaseTempSql();
            return queryTemp?.replace(_TAG_TEMP_WHERES_CONDITIONS, condition?.toWhereSql() ?: "");
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryWhere= toSql();
        return query.replace(_TAG_TEMP_WHERES, queryWhere ?: "");
    }


}