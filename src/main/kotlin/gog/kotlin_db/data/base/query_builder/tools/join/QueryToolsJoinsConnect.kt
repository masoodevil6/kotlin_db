package gog.kotlin_db.data.base.query_builder.tools.join

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups.QueryToolsConditionsGroups
import gog.kotlin_db.data.base.query_builder.tools.join.itemJoin.QueryToolsJoinsItem
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsJoinsConnect(

) :
    IQueryToolsJoinsConnect {

    protected var joins: MutableList<QueryToolsJoinsItem> = mutableListOf()


    companion object {

        /*----------------
          Joins
        ----------------*/
        const val _INNER_JOIN =     "join"
        const val _LEFT_JOIN =      "left join"
        const val _RIGHT_JOIN =     "right join"
    }




    override fun setupJoins(blockJoin: (IQueryToolsJoinsConnect) -> QueryToolsJoinsConnect): QueryToolsJoinsConnect {
        return  blockJoin(QueryToolsJoinsConnect());
    }








    override fun join(
        tableName: String,
        tableAlias: String ,
        blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups
    ): QueryToolsJoinsConnect {
        val condition = blockCondition(QueryToolsConditionsGroups(QueryToolsConditionsGroups._LOGICAL_AND));
        joins.add(
            QueryToolsJoinsItem(
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
        blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups
    ): QueryToolsJoinsConnect {
        val condition = blockCondition(QueryToolsConditionsGroups(QueryToolsConditionsGroups._LOGICAL_AND));
        joins.add(
            QueryToolsJoinsItem(
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
        blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups
    ): QueryToolsJoinsConnect {
        val condition = blockCondition(QueryToolsConditionsGroups(QueryToolsConditionsGroups._LOGICAL_AND));
        joins.add(
            QueryToolsJoinsItem(
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
        blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups
    ): QueryToolsJoinsConnect {
        val condition = blockCondition(QueryToolsConditionsGroups(QueryToolsConditionsGroups._LOGICAL_AND));
        joins.add(
            QueryToolsJoinsItem(
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
        blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups
    ): QueryToolsJoinsConnect {
        val condition = blockCondition(QueryToolsConditionsGroups(QueryToolsConditionsGroups._LOGICAL_AND));
        joins.add(
            QueryToolsJoinsItem(
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
        blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups
    ): QueryToolsJoinsConnect {
        val condition = blockCondition(QueryToolsConditionsGroups(QueryToolsConditionsGroups._LOGICAL_AND));
        joins.add(
            QueryToolsJoinsItem(
                _RIGHT_JOIN ,
                tableName ,
                tableName ,
                condition.toSql().toString()
            )
        )
        return this;
    }










    override fun getBaseTempSql(): String? {
        return "";
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getJoinSql(joins);
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_JOINS, querySelect ?: "");
    }



}