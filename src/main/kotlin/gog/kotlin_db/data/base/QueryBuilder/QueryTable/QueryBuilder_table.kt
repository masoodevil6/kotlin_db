package gog.kotlin_db.data.base.QueryBuilder.QueryTable

import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

class QueryBuilder_table(

) : IQueryBuilder {


    var tableName : String? = null
    var aliasName : String? = null


    private val tableNameTag = "{{TABLE_NAME_TAG}}"
    private val aliasNameTag = "{{ALIAS_NAME_TAG}}"





    override fun getBaseTempSql(): String? {
        return " from $tableNameTag as $aliasNameTag" ;
    }

    override fun toSql(): String? {
        if (tableName != null) {
            aliasName = if (aliasName == null ){ tableName }else{ aliasName }
            var queryTemp = getBaseTempSql();
            queryTemp = tableName?.let { queryTemp?.replace(tableNameTag, it) };
            queryTemp = aliasName?.let { queryTemp?.replace(aliasNameTag, it) };
            return queryTemp;
        }
        return null;
    }




    override fun replaceInBaseTemp(query: String): String {
        val queryFrom = toSql();
        return query.replace(QueryBuilder._TAG_TEMP_TABLE, queryFrom ?: "");
    }


}