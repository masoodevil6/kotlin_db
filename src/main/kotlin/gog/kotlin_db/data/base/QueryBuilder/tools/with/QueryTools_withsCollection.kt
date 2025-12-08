package gog.kotlin_db.data.base.QueryBuilder.tools.with

import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.tools.with.itemWith.QueryTools_withItem

class QueryTools_withsCollection(

) :
    IQueryTools_withsCollection
{

    protected var withs: MutableList<QueryTools_withItem> = mutableListOf()




    override fun setupWiths(blockWiths: (IQueryTools_withsCollection) -> QueryTools_withsCollection): QueryTools_withsCollection {
        return  blockWiths(QueryTools_withsCollection());
    }

    override fun with(
        withName: String,
        blockQuery: (QueryBuilder) -> QueryBuilder
    ): IQueryTools_withsCollection {
        val query = blockQuery(QueryBuilder());
        withs.add(
            QueryTools_withItem(
                withName ,
                query.toSql().toString()
            )
        )
        return this;
    }






    companion object {
        const val _TAG_TEMP_WITH=          "{{_TAG_TEMP_WITH}}"
        const val _TAG_TEMP_WITH_ITEM=     "{{_TAG_TEMP_WITH_ITEM}}"
    }



    override fun getBaseTempSql(): String? {
        return " with $_TAG_TEMP_WITH_ITEM " ;
    }

    override fun toSql(): String? {
        if (withs != null && withs.size > 0) {
            var queryTemp = getBaseTempSql();

            var withStr = "";
            for ((index, with) in withs.withIndex()){
                withStr +=  " ${with.toSql()}";
                if (index < withs.size - 1){
                    withStr += ","
                }
            }
            return queryTemp?.replace(_TAG_TEMP_WITH_ITEM, withStr);
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(_TAG_TEMP_WITH, querySelect ?: "");
    }



}