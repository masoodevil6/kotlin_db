package gog.my_project.query.query_builder.tools.column

import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect

class QueryToolsColumnsBase(
    private val sqlDialect: ISqlDialect,
    ) :
    IQueryToolsColumnsBase
{


    private var columnPrefix: String? = "";
    private var columnName: String? = "";


    override fun getColumnPrefix(): String? {
        return columnPrefix;
    }

    override fun getColumnName(): String? {
        return columnName;
    }






    override fun toSql(): String? {
        return sqlDialect.getColumnBaseSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }





    override fun columnPrefix(columnPrefix: String): IQueryToolsColumnsBase {
        this.columnPrefix = columnPrefix;
        return this;
    }

    override fun columnName(columnName: String): IQueryToolsColumnsBase {
        this.columnName = columnName;
        return this;
    }


}