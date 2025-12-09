package gog.kotlin_db.data.base.QueryBuilder.tools.join

import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroups
import gog.kotlin_db.data.base.QueryBuilder.tools.join.itemJoin.QueryTools_joinsItem

class QueryTools_joinsConnect(

) :
    IQueryTools_joinsConnect {

    protected var joins: MutableList<QueryTools_joinsItem> = mutableListOf()


    companion object {
        const val _TAG_TEMP_JOINS=         "{{_TAG_TEMP_JOINS}}"
        const val _TAG_TEMP_JOINS_ITEM=    "{{_TAG_TEMP_JOINS_ITEM}}"

        /*----------------
          Joins
        ----------------*/
        const val _INNER_JOIN =     "join"
        const val _LEFT_JOIN =      "left join"
        const val _RIGHT_JOIN =     "right join"
    }




    override fun setupJoins(blockJoin: (IQueryTools_joinsConnect) -> QueryTools_joinsConnect): QueryTools_joinsConnect {
        return  blockJoin(QueryTools_joinsConnect());
    }








    override fun join(
        tableName: String,
        tableAlias: String ,
        blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups
    ): QueryTools_joinsConnect {
        val condition = blockCondition(QueryTools_conditionsGroups(QueryTools_conditionsGroups._LOGICAL_AND));
        joins.add(
            QueryTools_joinsItem(
                _INNER_JOIN ,
                tableName ,
                tableAlias ,
                condition.toSql().toString()
            )
        )
        return this;
    }

    override fun join(
        tableName: String,
        blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups
    ): QueryTools_joinsConnect {
        val condition = blockCondition(QueryTools_conditionsGroups(QueryTools_conditionsGroups._LOGICAL_AND));
        joins.add(
            QueryTools_joinsItem(
                _INNER_JOIN ,
                tableName ,
                tableName ,
                condition.toSql().toString()
            )
        )
        return this;
    }








    override fun leftJoin(
        tableName: String,
        tableAlias: String,
        blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups
    ): QueryTools_joinsConnect {
        val condition = blockCondition(QueryTools_conditionsGroups(QueryTools_conditionsGroups._LOGICAL_AND));
        joins.add(
            QueryTools_joinsItem(
                _LEFT_JOIN ,
                tableName ,
                tableAlias ,
                condition.toSql().toString()
            )
        )
        return this;
    }

    override fun leftJoin(
        tableName: String,
        blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups
    ): QueryTools_joinsConnect {
        val condition = blockCondition(QueryTools_conditionsGroups(QueryTools_conditionsGroups._LOGICAL_AND));
        joins.add(
            QueryTools_joinsItem(
                _LEFT_JOIN ,
                tableName ,
                tableName ,
                condition.toSql().toString()
            )
        )
        return this;
    }







    override fun rightJoin(
        tableName: String,
        tableAlias: String,
        blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups
    ): QueryTools_joinsConnect {
        val condition = blockCondition(QueryTools_conditionsGroups(QueryTools_conditionsGroups._LOGICAL_AND));
        joins.add(
            QueryTools_joinsItem(
                _RIGHT_JOIN ,
                tableName ,
                tableAlias ,
                condition.toSql().toString()
            )
        )
        return this;
    }

    override fun rightJoin(
        tableName: String,
        blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups
    ): QueryTools_joinsConnect {
        val condition = blockCondition(QueryTools_conditionsGroups(QueryTools_conditionsGroups._LOGICAL_AND));
        joins.add(
            QueryTools_joinsItem(
                _RIGHT_JOIN ,
                tableName ,
                tableName ,
                condition.toSql().toString()
            )
        )
        return this;
    }










    override fun getBaseTempSql(): String? {
        return "$_TAG_TEMP_JOINS_ITEM" ;
    }

    override fun toSql(): String? {
        if (joins != null && joins.size > 0) {
            var queryTemp = getBaseTempSql();

            var joinsStr = "";
            for ((index, join) in joins.withIndex()){
                joinsStr +=  "${join.toSql()} ";
            }
            return queryTemp?.replace(_TAG_TEMP_JOINS_ITEM, joinsStr);
        }
        return "";
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(_TAG_TEMP_JOINS, querySelect ?: "");
    }



}