package gog.my_project.query.query_builder.tools.column

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionOrder


class QueryToolsColumns(
    private val sqlDialect: ISqlDialect ,

) :
    IQueryToolsColumns
{


    private var ColumnMethod: String? = null;
    private var ColumnName: String? = "";
    private var ColumnAlias: String? = null;



    override fun getColumnMethod(): String? {
        return ColumnMethod;
    }

    override fun getColumnName(): String? {
        return ColumnName;
    }

    override fun getColumnAlias(): String? {
        return ColumnAlias;
    }






    override fun toSql(): String? {
        return sqlDialect.getColumnSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }







    override fun sum(): IQueryToolsColumns {
        this.ColumnMethod = "sum";
        return this;
    }

    override fun count(): IQueryToolsColumns {
        this.ColumnMethod = "count";
        return this;
    }

    override fun avg(): IQueryToolsColumns {
        this.ColumnMethod = "avg";
        return this;
    }

    override fun min(): IQueryToolsColumns {
        this.ColumnMethod = "min";
        return this;
    }

    override fun max(): IQueryToolsColumns {
        this.ColumnMethod = "max";
        return this;
    }




    override fun column(columnName: String): IQueryToolsColumns {
        this.ColumnName = columnName;
        return this;
    }

    override fun column(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsColumns {
        val builder = QueryToolsColumnsBase(sqlDialect);
        val query = builder.blockColumn().toSql();
        if (query != null){
            this.ColumnName = query;
        }
        return this;
    }

    override fun columnQuery(blockQuery: IQueryBuilder.() -> IQueryBuilder): IQueryToolsColumns {
        val builder = QueryBuilder(sqlDialect);
        val query = builder.blockQuery().toSql();
        if (query != null){
            this.ColumnName = query;
        }
        return this;
    }




    override fun alias(alias: String): IQueryToolsColumns {
        this.ColumnAlias = alias;
        return this;
    }


}