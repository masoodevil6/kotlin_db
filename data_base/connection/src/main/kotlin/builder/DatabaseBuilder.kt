package gog.my_project.data_base.builder.base

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DatabaseConfigBuilder
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import java.sql.Connection
import java.sql.Date
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Time
import java.sql.Timestamp
import java.sql.Types
import kotlin.use

class DatabaseBuilder {

    private var config: DatabaseConfig = DefaultDatabaseConfig.config
    private var dialect: DialectQuery = DefaultDatabaseConfig.dialect;

    fun config(block: DatabaseConfigBuilder.() -> Unit): DatabaseBuilder {
        val builder = DatabaseConfigBuilder()
        builder.block()
        config = builder.build()
        return this
    }

    fun dialect(dialect: DialectQuery): DatabaseBuilder {
        this.dialect = dialect
        return this
    }

    fun getDialect(): DialectQuery = dialect

    fun build(): Connection {
        return try {
            DriverManager.getConnection(
                config.getDbUrl(),
                config.dbUserName ,
                config.dbPassword
            )
        }
        catch (e: SQLException) {
            throw RuntimeException("can not connect to database")
        }
    }




    fun execute(
        builtQuery: BuiltQuery,
        blockExecute: (ResultSet?) -> Unit
    ) {
        val query = builtQuery.getReadyQuery();
        val params =  builtQuery.params;
        val paramsNames = builtQuery.getListParamNames();

        val connection = build();

        connection.use {conn->
            conn.prepareStatement(query).use { stmt ->
                val ps = this.readyParamsInQuery(stmt , params , paramsNames);

                val resultExecute = ps?.executeQuery();
                blockExecute(resultExecute);
            }
        }
    }

    private fun readyParamsInQuery(ps : PreparedStatement?, params :MutableList<SqlParameter<*>>, paramsNames: List<String>) : PreparedStatement?{
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