package gog.kotlin_db.data.base.QueryBuilder.tools.where


import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroups


class QueryTools_where(

) : IQueryTools_where{

    companion object {
        const val _TAG_TEMP_WHERES=                "{{_TAG_TEMP_WHERES}}"
        const val _TAG_TEMP_WHERES_CONDITIONS=     "{{_TAG_TEMP_WHERES_CONDITIONS}}"
    }




    var condition: QueryTools_conditionsGroups? = null;

    override fun whereSetup(blockGroup: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_where {
        condition = blockGroup(QueryTools_conditionsGroups(QueryTools_conditionsGroups._LOGICAL_AND))
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