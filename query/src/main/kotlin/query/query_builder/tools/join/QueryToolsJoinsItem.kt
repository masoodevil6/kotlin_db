package gog.my_project.query.query_builder.tools.join

import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem


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