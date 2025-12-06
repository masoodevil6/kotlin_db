package gog.kotlin_db.data.base.QueryBuilder.QueryWhere

import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.Conditions.QueryConditionGroups.QueryBuilder_conditionsGroup


class QueryBuilder_where(

) : IQueryBuilder{


    var condition: QueryBuilder_conditionsGroup? = null;
    private val _whereConditionsTag = "{{WHERE_CONDITIONS_TAG}}"





    override fun getBaseTempSql(): String? {
        return " where $_whereConditionsTag " ;
    }


    override fun toSql(): String? {
        val conditionStr = condition?.toWhereSql();
        if (conditionStr != null) {
            var queryTemp = getBaseTempSql();
            return queryTemp?.replace(_whereConditionsTag, condition?.toWhereSql() ?: "");
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryWhere= toSql();
        return query.replace(QueryBuilder._TAG_TEMP_WHERES, queryWhere ?: "");
    }

}