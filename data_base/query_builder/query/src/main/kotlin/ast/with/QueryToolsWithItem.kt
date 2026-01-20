package gog.my_project.data_base.query_builder.query.ast.with

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithItem

class QueryToolsWithItem(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsWithItem
{

    private var withName : String? = null;
    private var withBody: IQueryBuilder? = null



    /* ==============================================================
    template
    ============================================================== */
    override fun getWithName(): String? {
        return withName;
    }

    override fun getWithBody(): IQueryBuilder? {
        return withBody;
    }




    /* ==============================================================
    Builder
    ============================================================== */
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getWithItemSql(this);
    }*/



    /* ==============================================================
    structure
    ============================================================== */
    override fun withName(withName: String): IQueryToolsWithItem {
        this.withName = withName;
        return this;
    }


    override fun withBody(blockWith: IQueryBuilder.() -> IQueryBuilder): IQueryToolsWithItem {
        val builder = QueryBuilder(params);
        this.withBody = builder.blockWith();
        return this;
    }






}