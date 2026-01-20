package gog.my_project.data_base.query_builder.query.ast.with

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithItem
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithsCollection


class QueryToolsWithsCollection(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsWithsCollection
{


    protected var withs: MutableList<IQueryToolsWithItem> = mutableListOf()


    /* ==============================================================
    template
    ============================================================== */
    override fun getListWiths(): MutableList<IQueryToolsWithItem> {
        return withs;
    }




    /* ==============================================================
    Builder
    ============================================================== */
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getWithSql(this);
    }*/



    /* ==============================================================
    structure
    ============================================================== */
    override fun addWith(blockWith: IQueryToolsWithItem.() -> IQueryToolsWithItem): IQueryToolsWithsCollection {
        val builder = QueryToolsWithItem(params);
        withs.add(builder.blockWith());
        return this;
    }



}