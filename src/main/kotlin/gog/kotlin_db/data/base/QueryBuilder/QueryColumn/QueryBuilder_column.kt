package gog.kotlin_db.data.base.QueryBuilder.QueryColumn

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

class QueryBuilder_column(
    val ColumnStr: String,
    val ColumnAlias: String? = null,
    val ColumnMethod: String? = null,
) : IQueryTools{


    private val ColumnMethodTag = "{{COLUMN_METHOD_TAG}}"
    private val ColumnStrTag =    "{{COLUMN_STR_TAG}}"
    private val ColumnAliasTag =  "{{COLUMN_ALIAS_TAG}}"


    override fun getBaseTempSql(): String? {
        var queryTemp: String = ""
        if (ColumnMethod != null) {
            queryTemp += ColumnMethodTag;
        }
        queryTemp += "($ColumnStrTag)"
        if (ColumnAlias != null) {
            queryTemp += " as $ColumnAliasTag ";
        }
        return queryTemp ;
    }


    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp = queryTemp?.replace(ColumnStrTag, ColumnStr);
        if (ColumnMethod != null) {
            queryTemp = queryTemp?.replace(ColumnMethodTag, ColumnMethod);
        }
        if (ColumnAlias != null) {
            queryTemp = queryTemp?.replace(ColumnAliasTag, ColumnAlias);
        }
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}