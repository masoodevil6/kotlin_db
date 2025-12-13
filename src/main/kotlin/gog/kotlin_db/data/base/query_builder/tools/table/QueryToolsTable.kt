package gog.kotlin_db.data.base.query_builder.tools.table

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.data.base.query_builder.QueryBuilder
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsTable(

) : IQueryToolsTable {

    var _tableName : String? = null
    var _tableAlias : String? = null



    override fun tableSetup(blockTable: (IQueryToolsTable) -> QueryToolsTable): QueryToolsTable {
        return  blockTable(QueryToolsTable());
    }


    override fun table(tableName: String?): QueryToolsTable {
        this._tableName = tableName;
        return this;
    }

    override fun table(
        tableName: String?,
        aliasName: String?
    ): QueryToolsTable {
        this._tableName = tableName;
        this._tableAlias = aliasName;
        return this;
    }

    override fun table(
        aliasName: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryToolsTable {
        val tableName = block(QueryBuilder());
        this._tableName = "(${tableName.toSql().toString()})";
        this._tableAlias = aliasName;
        return this;
    }



    override fun getBaseTempSql(): String? {
        return "";
    }

    override fun toSql(): String? {
        if (_tableName != null) {
            return DatabaseConfig.dbTypeName.getTableSql(_tableName!! , _tableAlias);
        }
        return "";
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryFrom = toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_TABLES, queryFrom ?: "");
    }




}