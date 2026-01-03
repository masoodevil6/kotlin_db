package gog.my_project.query.query_builder.tools.column

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder

class QueryToolsColumnsBase(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
) :
    IQueryToolsColumnsBase
{


    private var columnPrefix: String? = null;
    private var columnName:   String? = null;
    private var columnQuery:  IQueryBuilder? = null;



    /* ==============================================================
    template
    ============================================================== */
    override fun getColumnPrefix(): String? {
        return columnPrefix;
    }

    override fun getColumnName(): String? {
        return columnName;
    }

    override fun getColumnQuery(): IQueryBuilder? {
        return columnQuery;
    }




    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getColumnBaseSql(this);
    }




    /* ==============================================================
    structure
    ============================================================== */
    override fun columnPrefix(columnPrefix: String): IQueryToolsColumnsBase {
        this.columnPrefix = columnPrefix;
        return this;
    }

    override fun columnName(columnName: String): IQueryToolsColumnsBase {
        this.columnName = columnName;
        return this;
    }

    override fun columnQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsColumnsBase {
        val builder = QueryBuilder();
        this.columnQuery = builder.block();
        return this;
    }


}