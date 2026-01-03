package gog.my_project.query.interfaces.query_builders.tools

import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.tools.scripts.StringTools

interface IQueryTools {

    fun toSql(sqlDialect: ISqlDialect): String?;

    fun toSqlReadable(sqlDialect: ISqlDialect): String{
        return StringTools.formatSql(this.toSql(sqlDialect).toString());
    };

}