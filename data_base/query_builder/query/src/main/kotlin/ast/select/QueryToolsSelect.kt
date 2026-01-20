package gog.my_project.data_base.query_builder.query.ast.select

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.column.QueryToolsColumns
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumns
import gog.my_project.data_base.query_builder.query.interfaces.select.IQueryToolsSelect


class QueryToolsSelect(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsSelect
{


    protected var columns: MutableList<IQueryToolsColumns> = mutableListOf()



    /* ==============================================================
    template
    ============================================================== */
    override fun getListColumns(): MutableList<IQueryToolsColumns> {
        return columns;
    }



    /* ==============================================================
    Builder
    ============================================================== */
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getSelectSql(this);
    }*/





    /* ==============================================================
    structure
    ============================================================== */
    override fun addColumn(blockColumn: IQueryToolsColumns.() -> IQueryToolsColumns): IQueryToolsSelect {
        val builder = QueryToolsColumns(params);
        columns.add( builder.blockColumn());
        return this;
    }





}