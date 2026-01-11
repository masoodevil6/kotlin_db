package gog.my_project.query.query_builder.tools.with.collections

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.tools.with.collections.IQueryToolsWithsCollection
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.with.item.QueryToolsWithItem

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
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getWithSql(this);
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun addWith(blockWith: IQueryToolsWithItem.() -> IQueryToolsWithItem): IQueryToolsWithsCollection {
        val builder = QueryToolsWithItem(params);
        withs.add(builder.blockWith());
        return this;
    }



}