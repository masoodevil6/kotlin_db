package gog.kotlin_db.data.base.query_builder.tools.with

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.data.base.query_builder.QueryBuilder
import gog.kotlin_db.data.base.query_builder.tools.with.itemWith.QueryToolsWithItem
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsWithsCollection(

) :
    IQueryToolsWithsCollection
{

    protected var withs: MutableList<QueryToolsWithItem> = mutableListOf()




    override fun setupWiths(blockWiths: (IQueryToolsWithsCollection) -> QueryToolsWithsCollection): QueryToolsWithsCollection {
        return  blockWiths(QueryToolsWithsCollection());
    }

    override fun with(
        withName: String,
        blockQuery: (QueryBuilder) -> QueryBuilder
    ): QueryToolsWithsCollection {
        val query = blockQuery(QueryBuilder());
        withs.add(
            QueryToolsWithItem(
                withName ,
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