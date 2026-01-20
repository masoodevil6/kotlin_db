package gog.my_project.data_base.query_builder.query.ast.options

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.column.QueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionGroup

class QueryToolsOptionGroup(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
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
   /* override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionGroupSql(this);
    }*/






    /* ==============================================================
    structure
    ============================================================== */
    override fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionGroup {
        val builder = QueryToolsColumnsBase(params);
        val column = builder.blockColumn();
        _groupByList.add(column)
        return this;
    }



}