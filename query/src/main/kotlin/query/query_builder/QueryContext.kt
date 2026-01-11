package gog.my_project.query.query_builder

import gog.my_project.data_base.base.DatabaseBuilder
import gog.my_project.datas.BuiltQuery
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.sql_dialect.SqlDialectFactory
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types

class QueryContext(
    val dbBuilder : DatabaseBuilder
) {


    private var queryBuilder: IQueryBuilder = QueryBuilder();
    private var builtQuery: BuiltQuery? = null;

    private val sqlDialect: ISqlDialect =
        SqlDialectFactory().create(dbBuilder.getDialect())

    fun setQuery(query: IQueryBuilder) : QueryContext{
        queryBuilder = query;
        return this;
    }

    fun execute() : PreparedStatement?{
        var ps : PreparedStatement? = null;

        this.builtQuery = queryBuilder.readyExecuteSql(sqlDialect);
        val query = this.builtQuery!!.query;
        val params =  this.builtQuery!!.params;

        if (query != null) {
            val paramsNames = this.getAllListParamsInQuery(query);
            val queryForExecute = this.getQueryReplacedWithBind(query , paramsNames);

            println(queryForExecute)

            val db: Connection = dbBuilder.build();
            ps = db.prepareStatement(queryForExecute)

            for ((index , paramName) in paramsNames.withIndex()) {
                for (paramData in params) {
                    if (paramName == paramData.name) {
                        println(index+1)
                        when (paramData.sqlType) {
                            Types.NULL ->       ps.setNull(index+1  , java.sql.Types.NULL)
                            Types.INTEGER ->    ps.setInt(index+1, paramData.value as Int)
                            Types.BIGINT ->     ps.setInt(index+1, paramData.value as Int)
                            Types.DOUBLE ->     ps.setDouble(index+1, paramData.value as Double)
                            Types.FLOAT ->      ps.setFloat(index+1, paramData.value as Float)
                            Types.BOOLEAN ->    ps.setBoolean(index+1, paramData.value as Boolean)
                            Types.VARCHAR ->    ps.setString(index+1, paramData.value as String?)
                            Types.DATE ->       ps.setDate(index+1, paramData.value as Date?)
                            Types.TIME ->       ps.setDate(index+1, paramData.value as Date?)
                            Types.TIMESTAMP ->  ps.setTimestamp(index+1, paramData.value as Timestamp?)
                            else ->             ps.setObject(index+1, paramData.value);
                        }

                    }
                }
            }
        }

        return ps;
    }


    private fun getAllListParamsInQuery(query: String?) : List<String>{
        val paramsNames = mutableSetOf<String>()
        if (query != null) {
            val pattern = Regex(":([a-zA-Z_][a-zA-Z0-9_]*)");
            pattern.findAll(query).forEach { match->
                paramsNames.add(match.groupValues[1])
            };
        }
        return paramsNames.toList();
    }

    private fun getQueryReplacedWithBind(query: String? , paramsNames : List<String>) : String?{
        var resultExp: String? = null
        if (query != null) {
            resultExp = query;
            for (paramName in paramsNames) {
                resultExp = resultExp?.replace(":$paramName" , "?")
            }
        }
        return resultExp;
    }




    /*fun createQueryBuilder(): IQueryBuilder {
        return QueryBuilder()
    }*/
}