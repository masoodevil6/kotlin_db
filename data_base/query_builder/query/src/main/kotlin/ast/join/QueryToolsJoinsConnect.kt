package gog.my_project.data_base.query_builder.query.ast.join

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsItem

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
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getJoinSql(this);
    }*/





    /* ==============================================================
    structure
    ============================================================== */
    override fun addJoin(blockJoin: IQueryToolsJoinsItem.() -> IQueryToolsJoinsItem): IQueryToolsJoinsConnect {
        val builder = QueryToolsJoinsItem(params);
        joins.add(builder.blockJoin());
        return this;
    }



}