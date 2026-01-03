package gog.my_project.query.query_builder.tools.column

import gog.my_project.enums.SqlMethodColumn
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionOrder


class QueryToolsColumns(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
) :
    IQueryToolsColumns
{


    private var ColumnMethod: SqlMethodColumn? = null;
    private var ColumnName:   IQueryToolsColumnsBase? = null;
    private var ColumnAlias:  String? = null;





    /* ==============================================================
    template
    ============================================================== */
    override fun getColumnMethod(): SqlMethodColumn? {
        return ColumnMethod;
    }

    override fun getColumnName(): IQueryToolsColumnsBase? {
        return ColumnName;
    }

    override fun getColumnAlias(): String? {
        return ColumnAlias;
    }





    /* ==============================================================
    Builder
    ============================================================== */

    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getColumnSql(this);
    }





    /* ==============================================================
    structure
    ============================================================== */
    override fun method(method: SqlMethodColumn): IQueryToolsColumns {
        this.ColumnMethod = method;
        return this;
    }

    override fun sum(): IQueryToolsColumns {
        this.ColumnMethod = SqlMethodColumn.Sum;
        return this;
    }

    override fun count(): IQueryToolsColumns {
        this.ColumnMethod = SqlMethodColumn.Count;
        return this;
    }

    override fun avg(): IQueryToolsColumns {
        this.ColumnMethod = SqlMethodColumn.Avg;
        return this;
    }

    override fun min(): IQueryToolsColumns {
        this.ColumnMethod = SqlMethodColumn.Min;
        return this;
    }

    override fun max(): IQueryToolsColumns {
        this.ColumnMethod = SqlMethodColumn.Max;
        return this;
    }







    override fun column(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsColumns {
        val builder = QueryToolsColumnsBase();
        this.ColumnName = builder.blockColumn();
        return this;
    }





    override fun alias(alias: String): IQueryToolsColumns {
        this.ColumnAlias = alias;
        return this;
    }


}