package gog.kotlin_db.data.base.query_builder.tools.column

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsColumns(
    val ColumnName: String,
    val ColumnAlias: String? = null,
    val ColumnMethod: String? = null,
) : IQueryToolsColumns{

    override fun getBaseTempSql(): String? {
        return "";
       // return DatabaseConfig.dbTypeName.getColumnSql();
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getColumnSql( ColumnName, ColumnMethod , ColumnAlias);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }
}