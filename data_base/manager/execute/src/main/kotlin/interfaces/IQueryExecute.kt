package gog.my_project.data_base.manager.execute.interfaces


import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.data_base.DatabaseServerInfo
import gog.my_project.data_base.manager.connection.manager.DatabaseConnection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Time
import java.sql.Timestamp
import java.sql.Types
import kotlin.use

interface IQueryExecute {

    /** Reads and parses server identity at the existing database manager boundary. */
    fun getDatabaseServerInfo(blockExecute: (ExecuteResult<DatabaseServerInfo>) -> Unit) {
        executeSelect(BuiltQuery("SELECT VERSION()", mutableListOf())) { result ->
            when (result) {
                is ExecuteResult.Failure -> blockExecute(result)
                is ExecuteResult.Success -> {
                    try {
                        val rawVersion = result.result?.use { rows ->
                            if (rows.next()) rows.getString(1) else null
                        }
                        if (rawVersion == null) {
                            blockExecute(ExecuteResult.Failure(IllegalStateException("Database did not return its server version")))
                        } else {
                            blockExecute(ExecuteResult.Success(DatabaseServerInfo.parse(rawVersion)))
                        }
                    } catch (error: Throwable) {
                        blockExecute(ExecuteResult.Failure(error))
                    }
                }
            }
        }
    }


    /// -----------------------------------------------------
    /// Crud level
    /// -----------------------------------------------------

    fun executeSelect(
        builtQuery:      BuiltQuery,
        blockExecute:    (ExecuteResult<ResultSet>) -> Unit
    );

    fun executeUpdate(
        builtQuery:      BuiltQuery,
        blockExecute:    (ExecuteResult<Int>) -> Unit
    );

    fun executeInsert(
        builtQuery:      BuiltQuery,
        blockExecute:    (ExecuteResult<Long>) -> Unit
    );

    fun executeDelete(
        builtQuery:      BuiltQuery,
        blockExecute:    (ExecuteResult<Int>) -> Unit
    );




    /// -----------------------------------------------------
    /// Schema level
    /// -----------------------------------------------------

    fun executeTable(
        builtQuery:      BuiltQuery,
        blockExecute:    (ExecuteResult<Boolean>) -> Unit
    );




    /// -----------------------------------------------------
    /// TOOLS
    /// -----------------------------------------------------
    fun execute(
        builtQuery:     BuiltQuery,
        blockExecute:   (conn: Connection, query: String?, params: MutableList<SqlParameter<*>>, paramsName: List<String> , error: Throwable?) -> Unit
    ) {
        val query =        builtQuery.getReadyQuery();
        val params =       builtQuery.params;
        val paramsNames =  builtQuery.getListParamNames();
        val connection =   DatabaseConnection().build();

        connection.use {conn->
            if (query != null) {
                try {
                    blockExecute(conn, query , params , paramsNames , null)
                }
                catch (ex: SQLException){
                    blockExecute(conn, query , params , paramsNames , ex)
                }
            }
            else{
                blockExecute(conn, query , params , paramsNames , Throwable("Error executing query "))
            }
        }
    }



    fun readyParamsInQuery(ps : PreparedStatement?, params :MutableList<SqlParameter<*>>, paramsNames: List<String>) : PreparedStatement?{
        if (ps != null){
            for ((index , paramName) in paramsNames.withIndex()) {
                for (paramData in params) {
                    if (paramName == paramData.name) {
                        when (paramData.sqlType) {
                            Types.NULL ->       ps.setNull(index+1  , java.sql.Types.NULL)
                            Types.INTEGER ->    ps.setInt(index+1, paramData.value as Int)
                            Types.BIGINT ->     ps.setInt(index+1, paramData.value as Int)
                            Types.DOUBLE ->     ps.setDouble(index+1, paramData.value as Double)
                            Types.FLOAT ->      ps.setFloat(index+1, paramData.value as Float)
                            Types.BOOLEAN ->    ps.setBoolean(index+1, paramData.value as Boolean)
                            Types.VARCHAR ->    ps.setString(index+1, paramData.value as String?)
                            Types.DATE ->       ps.setDate(index+1, paramData.value as Date?)
                            Types.TIME ->       ps.setTime(index+1, paramData.value as Time?)
                            Types.TIMESTAMP ->  ps.setTimestamp(index+1, paramData.value as Timestamp?)
                            else ->             ps.setObject(index+1, paramData.value);
                        }

                    }
                }
            }
        }

        return ps;
    }

}
