package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionGroup
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.column.QueryToolsColumnsBase

class QueryToolsOptionGroup(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
):
    IQueryToolsOptionGroup
{

    protected var _groupByList: MutableList<IQueryToolsColumnsBase> = mutableListOf();



    /* ==============================================================
    template
    ============================================================== */
    override fun getListColumns(): MutableList<IQueryToolsColumnsBase> {
        return _groupByList;
    }





    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionGroupSql(this);
    }






    /* ==============================================================
    structure
    ============================================================== */
    override fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionGroup {
        val builder = QueryToolsColumnsBase();
        val column = builder.blockColumn();
        _groupByList.add(column)
        return this;
    }



}