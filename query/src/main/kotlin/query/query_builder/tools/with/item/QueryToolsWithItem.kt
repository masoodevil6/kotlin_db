package gog.my_project.query.query_builder.tools.with.item

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder

class QueryToolsWithItem(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
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
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getWithItemSql(this);
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun withName(withName: String): IQueryToolsWithItem {
        this.withName = withName;
        return this;
    }


    override fun withBody(blockWith: IQueryBuilder.() -> IQueryBuilder): IQueryToolsWithItem {
        val builder = QueryBuilder();
        this.withBody = builder.blockWith();
        return this;
    }






}