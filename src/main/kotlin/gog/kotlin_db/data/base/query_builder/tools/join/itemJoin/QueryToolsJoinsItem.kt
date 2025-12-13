package gog.kotlin_db.data.base.query_builder.tools.join.itemJoin

import gog.kotlin_db.Configs.DatabaseConfig

class QueryToolsJoinsItem(
    val joinConnection: String ,
    val joinTable: String ,
    val joinAlias: String ,
    val joinConditions: String
) :
    IQueryToolsJoinsItem
{

    override fun getBaseTempSql(): String? {
        return "";
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getJoinItemSql(joinConnection , joinTable , joinAlias , joinConditions);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}