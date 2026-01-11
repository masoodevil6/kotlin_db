package gog.my_project.query.interfaces.query_builders.tools

import gog.my_project.core.sql_dialect.DialectQuery
import gog.my_project.datas.BuiltQuery
import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.tools.scripts.StringTools

interface IQueryTools {

    var params: MutableList<SqlParameter<*>>

    fun toSql(sqlDialect: ISqlDialect): String?;

    fun readyExecuteSql(dialectQuery: ISqlDialect): BuiltQuery? {
        return BuiltQuery(
            this.toSql(dialectQuery) ,
            params
        );
    };
    
    fun toSqlReadable(sqlDialect: ISqlDialect): String{
        return StringTools.formatSql(this.toSql(sqlDialect).toString());
    };

}