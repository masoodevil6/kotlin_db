package gog.kotlin_db.data.base.QueryBuilder.tools.column

class QueryTools_columns(
    val ColumnName: String,
    val ColumnAlias: String? = null,
    val ColumnMethod: String? = null,
) : IQueryTools_columns{


    companion object {
        const val _TAG_TEMP_COLUMN_METHOD=     "{{_TAG_TEMP_COLUMN_METHOD}}"
        const val _TAG_TEMP_COLUMN_NAME=       "{{_TAG_TEMP_COLUMN_NAME}}"
        const val _TAG_TEMP_COLUMN_ALIAS=       "{{_TAG_TEMP_COLUMN_ALIAS}}"
    }

    override fun getBaseTempSql(): String? {
        var queryTemp: String = ""
        if (ColumnMethod != null) {
            queryTemp += _TAG_TEMP_COLUMN_METHOD;
        }
        queryTemp += "($_TAG_TEMP_COLUMN_NAME)"
        if (ColumnAlias != null) {
            queryTemp += " as $_TAG_TEMP_COLUMN_ALIAS ";
        }
        return queryTemp ;
    }

    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp = queryTemp?.replace(_TAG_TEMP_COLUMN_NAME, ColumnName);
        if (ColumnMethod != null) {
            queryTemp = queryTemp?.replace(_TAG_TEMP_COLUMN_METHOD, ColumnMethod);
        }
        if (ColumnAlias != null) {
            queryTemp = queryTemp?.replace(_TAG_TEMP_COLUMN_ALIAS, ColumnAlias);
        }
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }
}