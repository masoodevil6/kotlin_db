package gog.my_project.query.query_builder.tools.column

import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns


class QueryToolsColumns(
    val ColumnName: String,
    val ColumnAlias: String? = null,
    val ColumnMethod: String? = null,
) : IQueryToolsColumns {

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