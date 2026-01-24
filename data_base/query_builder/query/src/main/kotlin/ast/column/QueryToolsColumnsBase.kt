package gog.my_project.data_base.query_builder.query.ast.column

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase

class QueryToolsColumnsBase(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
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
        val builder = QueryBuilder(params);
        this.columnQuery = builder.block();
        return this;
    }


}