package gog.my_project.query.query_builder.tools.table

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder
import gog.my_project.query.query_builder.tools.column.QueryToolsColumns
import gog.my_project.tools.templates.OTemplateSqlDialect


class QueryToolsTable(
    private val sqlDialect: ISqlDialect
) : IQueryToolsTable {

    var _tableName : String? = null;
    var _tableAlias : String? = null;

    override fun getTableName(): String? {
        return _tableName;
    }

    override fun getTableAlias(): String? {
        return _tableAlias;
    }









    override fun table(tableName: String?): IQueryToolsTable {
        this._tableName = tableName;
        return this;
    }

    override fun table(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable {
        val builder = QueryBuilder(sqlDialect);
        val tableName = builder.block();
        this._tableName = "(${tableName.toSql().toString()})";
        return this;
    }





    override fun alias(aliasName: String?): IQueryToolsTable {
        this._tableAlias = aliasName;
        return this;
    }






    override fun toSql(): String? {
        if (_tableName != null) {
            return sqlDialect.getTableSql(this);
        }
        return "";
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryFrom = toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_TABLES, queryFrom ?: "");
    }




}