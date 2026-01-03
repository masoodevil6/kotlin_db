package gog.my_project.query.query_builder.tools.table

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder


class QueryToolsTable(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
) :
    IQueryToolsTable
{

    var _tableName : String? = null;
    var _tableAlias : String? = null;
    var _tableQuery : IQueryBuilder? = null;



    /* ==============================================================
    template
    ============================================================== */
    override fun getTableName(): String? {
        return _tableName;
    }

    override fun getTableAlias(): String? {
        return _tableAlias;
    }

    override fun getTableQuery(): IQueryBuilder? {
        return _tableQuery;
    }




    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getTableSql(this);
    }




    /* ==============================================================
    structure
    ============================================================== */
    override fun table(tableName: String?): IQueryToolsTable {
        this._tableName = tableName;
        return this;
    }

    override fun tableQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable {
        val builder = QueryBuilder();
        this._tableQuery = builder.block();
        return this;
    }





    override fun alias(aliasName: String?): IQueryToolsTable {
        this._tableAlias = aliasName;
        return this;
    }





}