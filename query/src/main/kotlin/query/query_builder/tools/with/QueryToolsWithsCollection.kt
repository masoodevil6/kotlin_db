package gog.my_project.query.query_builder.tools.with

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.with.IQueryToolsWithItem
import gog.my_project.query.interfaces.query_builders.tools.with.IQueryToolsWithsCollection
import gog.my_project.query.query_builder.QueryBuilder


class QueryToolsWithsCollection(

) :
    IQueryToolsWithsCollection
{

    protected var withs: MutableList<IQueryToolsWithItem> = mutableListOf()




    override fun setupWiths(blockWiths: (IQueryToolsWithsCollection) -> IQueryToolsWithsCollection): IQueryToolsWithsCollection {
        return  blockWiths(QueryToolsWithsCollection());
    }

    override fun with(withName: String ,  blockQuery: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsWithsCollection {
        val query = blockQuery(QueryBuilder());
        withs.add(
            QueryToolsWithItem(
                withName,
                query.toSql().toString()
            )
        )
        return this;
    }







    override fun getBaseTempSql(): String? {
        return "";
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getWithSql(withs);
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_WITH, querySelect ?: "");
    }



}