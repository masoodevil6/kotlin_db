package gog.my_project.query.query_builder.tools.table

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.query_builder.QueryBuilder


class QueryToolsTable(

) : IQueryToolsTable {

    var _tableName : String? = null
    var _tableAlias : String? = null



    override fun tableSetup(blockTable: (IQueryToolsTable) -> IQueryToolsTable): IQueryToolsTable {
        return  blockTable(QueryToolsTable());
    }


    override fun table(tableName: String?): IQueryToolsTable {
        this._tableName = tableName;
        return this;
    }

    override fun table(tableName: String? , aliasName: String?): IQueryToolsTable {
        this._tableName = tableName;
        this._tableAlias = aliasName;
        return this;
    }

    override fun table(aliasName: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsTable {
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