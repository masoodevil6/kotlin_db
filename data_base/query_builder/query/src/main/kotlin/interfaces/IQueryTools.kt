package gog.my_project.data_base.query_builder.query.interfaces

import gog.my_project.data_base.connection.tools.datas.SqlParameter


interface IQueryTools {

    var params: MutableList<SqlParameter<*>>

   /* fun getParams(): MutableList<SqlParameter<*>>{
        return params;
    }*/



    /*fun readyExecuteSql(): BuiltQuery {
        return BuiltQuery(
            this ,
            params
        );
    };
*/
    /*fun toSql(sqlDialect: ISqlDialect): String?;

fun toSqlReadable(sqlDialect: ISqlDialect): String{
    return StringTools.formatSql(this.toSql(sqlDialect).toString());
};*/



}