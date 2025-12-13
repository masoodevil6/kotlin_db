package gog.kotlin_db.data.base.query_builder.tools.with.itemWith

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

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