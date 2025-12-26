package gog.my_project.query.query_builder.tools.with

import gog.my_project.query.interfaces.query_builders.tools.with.IQueryToolsWithItem


class QueryToolsWithItem(
    val withName : String,
    val withBody: String
) :
    IQueryToolsWithItem
{


    override fun getBaseTempSql(): String? {
      return "";
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getWithItemSql(withName ,withBody );
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}