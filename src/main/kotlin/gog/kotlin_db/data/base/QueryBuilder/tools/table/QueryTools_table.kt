package gog.kotlin_db.data.base.QueryBuilder.tools.table

import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

class QueryTools_table(

) : IQueryTools_table {

    var _tableName : String? = null
    var _aliasName : String? = null



    override fun tableSetup(blockTable: (IQueryTools_table) -> QueryTools_table): QueryTools_table {
        return  blockTable(QueryTools_table());
    }


    override fun table(tableName: String?): QueryTools_table {
        this._tableName = tableName;
        return this;
    }

    override fun table(
        tableName: String?,
        aliasName: String?
    ): QueryTools_table {
        this._tableName = tableName;
        this._aliasName = aliasName;
        return this;
    }

    override fun table(
        aliasName: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_table {
        val tableName = block(QueryBuilder());
        this._tableName = "(${tableName.toSql().toString()})";
        this._aliasName = aliasName;
        return this;
    }



    companion object {
        const val _TAG_TEMP_TABLES=          "{{_TAG_TEMP_TABLES}}"
        const val _TAG_TEMP_TABLES_NAME=     "{{_TAG_TEMP_TABLES_NAME}}"
        const val _TAG_TEMP_TABLES_ALIAS=    "{{_TAG_TEMP_TABLES_ALIAS}}"
    }

    override fun getBaseTempSql(): String? {
        return " from $_TAG_TEMP_TABLES_NAME as $_TAG_TEMP_TABLES_ALIAS" ;
    }

    override fun toSql(): String? {
        if (_tableName != null) {
            _aliasName = if (_aliasName == null ){ _tableName }else{ _aliasName }
            var queryTemp = getBaseTempSql();
            queryTemp = queryTemp?.replace(_TAG_TEMP_TABLES_NAME, _tableName ?: "")
            queryTemp = queryTemp?.replace(_TAG_TEMP_TABLES_ALIAS, _aliasName ?: "")
            return queryTemp;
        }
        return "";
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryFrom = toSql();
        return query.replace(_TAG_TEMP_TABLES, queryFrom ?: "");
    }




}