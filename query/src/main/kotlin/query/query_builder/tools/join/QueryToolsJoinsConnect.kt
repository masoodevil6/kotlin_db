package gog.my_project.query.query_builder.tools.join

import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsConnect
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.tools.templates.OTemplateSqlDialect

class QueryToolsJoinsConnect(
    private val sqlDialect: ISqlDialect
) :
    IQueryToolsJoinsConnect {

    protected var joins: MutableList<IQueryToolsJoinsItem> = mutableListOf()

    override fun getListJoins(): List<IQueryToolsJoinsItem> {
        return joins;
    }



    override fun addJoin(blockJoin: IQueryToolsJoinsItem.() -> IQueryToolsJoinsItem): IQueryToolsJoinsConnect {
        val builder = QueryToolsJoinsItem(sqlDialect);
        joins.add(builder.blockJoin());
        return this;
    }





    override fun toSql(): String? {
        return sqlDialect.getJoinSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_JOINS, querySelect ?: "");
    }



}