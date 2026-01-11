package gog.my_project.query.query_builder.tools.join

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsConnect
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect

class QueryToolsJoinsConnect(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsJoinsConnect {

    protected var joins: MutableList<IQueryToolsJoinsItem> = mutableListOf()



    /* ==============================================================
    template
    ============================================================== */
    override fun getListJoins(): List<IQueryToolsJoinsItem> {
        return joins;
    }





    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getJoinSql(this);
    }





    /* ==============================================================
    structure
    ============================================================== */
    override fun addJoin(blockJoin: IQueryToolsJoinsItem.() -> IQueryToolsJoinsItem): IQueryToolsJoinsConnect {
        val builder = QueryToolsJoinsItem(params);
        joins.add(builder.blockJoin());
        return this;
    }



}