package gog.my_project.query.query_builder.tools.with.item

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder

class QueryToolsWithItem(
    private val sqlDialect: ISqlDialect,

    ) :
    IQueryToolsWithItem
{

    private var withName : String? = null;
    private var withBody: String? = null

    override fun getWithName(): String? {
        return withName;
    }

    override fun getWithBody(): String? {
        return withBody;
    }



    override fun withName(withName: String): IQueryToolsWithItem {
        this.withName = withName;
        return this;
    }

    override fun withBody(withBody: String): IQueryToolsWithItem {
        this.withBody = withBody;
        return this;
    }

    override fun withBody(blockWith: IQueryBuilder.() -> IQueryBuilder): IQueryToolsWithItem {
        val builder = QueryBuilder(sqlDialect);
        this.withBody = builder.blockWith().toSql();
        return this;
    }






    override fun toSql(): String? {
        return sqlDialect.getWithItemSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }




}