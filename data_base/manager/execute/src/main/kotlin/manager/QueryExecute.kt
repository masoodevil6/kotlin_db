package gog.my_project.data_base.manager.execute.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.manager.execute.connection.withOwnedConnection
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.query.BoundQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import kotlin.use

class QueryExecute : IQueryExecute {
    override fun executeSelect(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
    ) {
        executeBuiltQuery(builtQuery, blockExecute) { connection, sql, params, paramNames, callback ->
            executeBuiltSelect(connection, sql, params, paramNames, callback)
        }
    }

    override fun executeSelect(
        connection: Connection,
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
    ) {
        executeBuiltQueryOnConnection(connection, builtQuery, blockExecute) { conn, sql, params, paramNames, callback ->
            executeBuiltSelect(conn, sql, params, paramNames, callback)
        }
    }

    override fun executeSelect(
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
    ) = withOwnedConnection { connection -> executeSelect(connection, boundQuery, blockExecute) }

    override fun executeSelect(
        connection: Connection,
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
    ) {
        executeWithPreparedStatement(connection, boundQuery.jdbcSql, blockExecute = blockExecute) { statement ->
            val prepared = requireNotNull(readyParamsInQuery(statement, boundQuery))
            ExecuteResult.Success(prepared.executeQuery())
        }
    }

    override fun executeInsert(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Long>) -> Unit,
    ) {
        executeBuiltQuery(builtQuery, blockExecute) { connection, sql, params, paramNames, callback ->
            executeBuiltInsert(connection, sql, params, paramNames, callback)
        }
    }

    override fun executeInsert(
        connection: Connection,
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Long>) -> Unit,
    ) {
        executeBuiltQueryOnConnection(connection, builtQuery, blockExecute) { conn, sql, params, paramNames, callback ->
            executeBuiltInsert(conn, sql, params, paramNames, callback)
        }
    }

    override fun executeInsert(
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<Long>) -> Unit,
    ) = withOwnedConnection { connection -> executeInsert(connection, boundQuery, blockExecute) }

    override fun executeInsert(
        connection: Connection,
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<Long>) -> Unit,
    ) {
        executeWithPreparedStatement(
            connection,
            boundQuery.jdbcSql,
            returnGeneratedKeys = true,
            blockExecute = blockExecute,
        ) { statement ->
            val prepared = requireNotNull(readyParamsInQuery(statement, boundQuery))
            val affectedRows = prepared.executeUpdate()
            when {
                affectedRows <= 0 -> ExecuteResult.Failure(IllegalStateException("INSERT did not affect any rows"))
                else -> {
                    val generatedKey = prepared.generatedKeys.use { keys ->
                        if (keys.next()) keys.getLong(1) else null
                    }
                    if (generatedKey == null) {
                        ExecuteResult.Failure(IllegalStateException("No generated key returned"))
                    } else {
                        ExecuteResult.Success(generatedKey)
                    }
                }
            }
        }
    }

    override fun executeUpdate(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        executeBuiltQuery(builtQuery, blockExecute) { connection, sql, params, paramNames, callback ->
            executeBuiltUpdate(connection, sql, params, paramNames, callback)
        }
    }

    override fun executeUpdate(
        connection: Connection,
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        executeBuiltQueryOnConnection(connection, builtQuery, blockExecute) { conn, sql, params, paramNames, callback ->
            executeBuiltUpdate(conn, sql, params, paramNames, callback)
        }
    }

    override fun executeUpdate(
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) = withOwnedConnection { connection -> executeUpdate(connection, boundQuery, blockExecute) }

    override fun executeUpdate(
        connection: Connection,
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        executeWithPreparedStatement(connection, boundQuery.jdbcSql, blockExecute = blockExecute) { statement ->
            val prepared = requireNotNull(readyParamsInQuery(statement, boundQuery))
            ExecuteResult.Success(prepared.executeUpdate())
        }
    }

    override fun executeDelete(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        executeBuiltQuery(builtQuery, blockExecute) { connection, sql, params, paramNames, callback ->
            executeBuiltDelete(connection, sql, params, paramNames, callback)
        }
    }

    override fun executeDelete(
        connection: Connection,
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        executeBuiltQueryOnConnection(connection, builtQuery, blockExecute) { conn, sql, params, paramNames, callback ->
            executeBuiltDelete(conn, sql, params, paramNames, callback)
        }
    }

    override fun executeDelete(
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) = withOwnedConnection { connection -> executeDelete(connection, boundQuery, blockExecute) }

    override fun executeDelete(
        connection: Connection,
        boundQuery: BoundQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        executeWithPreparedStatement(connection, boundQuery.jdbcSql, blockExecute = blockExecute) { statement ->
            val prepared = requireNotNull(readyParamsInQuery(statement, boundQuery))
            ExecuteResult.Success(prepared.executeUpdate())
        }
    }

    override fun executeTable(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        executeBuiltQuery(builtQuery, blockExecute) { connection, sql, params, paramNames, callback ->
            executeBuiltTable(connection, sql, params, paramNames, callback)
        }
    }

    override fun executeTable(
        connection: Connection,
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        executeBuiltQueryOnConnection(connection, builtQuery, blockExecute) { conn, sql, params, paramNames, callback ->
            executeBuiltTable(conn, sql, params, paramNames, callback)
        }
    }

    private fun executeBuiltSelect(
        connection: Connection,
        sql: String,
        params: MutableList<out IQueryParameterBinding>,
        paramNames: List<String>,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
    ) = executeWithPreparedStatement(connection, sql, blockExecute = blockExecute) { statement ->
        val prepared = requireNotNull(readyParamsInQuery(statement, params, paramNames))
        ExecuteResult.Success(prepared.executeQuery())
    }

    private fun executeBuiltInsert(
        connection: Connection,
        sql: String,
        params: MutableList<out IQueryParameterBinding>,
        paramNames: List<String>,
        blockExecute: (ExecuteResult<Long>) -> Unit,
    ) = executeWithPreparedStatement(
        connection,
        sql,
        returnGeneratedKeys = true,
        blockExecute = blockExecute,
    ) { statement ->
        val prepared = requireNotNull(readyParamsInQuery(statement, params, paramNames))
        val affectedRows: Int? = prepared.executeUpdate()
        when {
            affectedRows == null -> ExecuteResult.Failure(
                IllegalStateException("INSERT did not report an affected-row count"),
            )
            affectedRows <= 0 -> ExecuteResult.Failure(IllegalStateException("INSERT did not affect any rows"))
            else -> {
                val generatedKey = prepared.generatedKeys.use { keys ->
                    if (keys.next()) keys.getLong(1) else null
                }
                if (generatedKey == null) {
                    ExecuteResult.Failure(IllegalStateException("No generated key returned"))
                } else {
                    ExecuteResult.Success(generatedKey)
                }
            }
        }
    }

    private fun executeBuiltUpdate(
        connection: Connection,
        sql: String,
        params: MutableList<out IQueryParameterBinding>,
        paramNames: List<String>,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) = executeWithPreparedStatement(connection, sql, blockExecute = blockExecute) { statement ->
        val prepared = requireNotNull(readyParamsInQuery(statement, params, paramNames))
        ExecuteResult.Success(prepared.executeUpdate())
    }

    private fun executeBuiltDelete(
        connection: Connection,
        sql: String,
        params: MutableList<out IQueryParameterBinding>,
        paramNames: List<String>,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) = executeBuiltUpdate(connection, sql, params, paramNames, blockExecute)

    private fun executeBuiltTable(
        connection: Connection,
        sql: String,
        params: MutableList<out IQueryParameterBinding>,
        paramNames: List<String>,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) = executeWithPreparedStatement(connection, sql, blockExecute = blockExecute) { statement ->
        val prepared = requireNotNull(readyParamsInQuery(statement, params, paramNames))
        prepared.execute() // The JDBC boolean is intentionally not exposed.
        ExecuteResult.Success(true)
    }

    private fun <T> executeBuiltQuery(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<T>) -> Unit,
        executeOnConnection: (
            Connection,
            String,
            MutableList<out IQueryParameterBinding>,
            List<String>,
            (ExecuteResult<T>) -> Unit,
        ) -> Unit,
    ) {
        execute(builtQuery) { connection, query, params, paramNames, error ->
            if (error != null) {
                blockExecute(ExecuteResult.Failure(error))
                return@execute
            }
            executeOnConnection(connection, requireNotNull(query), params, paramNames, blockExecute)
        }
    }

    private fun <T> executeBuiltQueryOnConnection(
        connection: Connection,
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<T>) -> Unit,
        executeOnConnection: (
            Connection,
            String,
            MutableList<out IQueryParameterBinding>,
            List<String>,
            (ExecuteResult<T>) -> Unit,
        ) -> Unit,
    ) {
        val query = builtQuery.getReadyQuery()
        val params = builtQuery.params
        val paramNames = builtQuery.getListParamNames()
        if (query == null) {
            blockExecute(ExecuteResult.Failure(IllegalArgumentException("BuiltQuery query must not be null")))
            return
        }
        executeOnConnection(connection, query, params, paramNames, blockExecute)
    }

    private fun <T> executeWithPreparedStatement(
        connection: Connection,
        sql: String,
        returnGeneratedKeys: Boolean = false,
        blockExecute: (ExecuteResult<T>) -> Unit,
        operation: (PreparedStatement) -> ExecuteResult<T>,
    ) {
        val statement = try {
            if (returnGeneratedKeys) {
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
            } else {
                connection.prepareStatement(sql)
            }
        } catch (exception: SQLException) {
            blockExecute(ExecuteResult.Failure(exception))
            return
        }

        statement.use { preparedStatement ->
            val result = try {
                operation(preparedStatement)
            } catch (exception: SQLException) {
                ExecuteResult.Failure(exception)
            }
            // User callbacks stay outside the JDBC catch and are invoked exactly once.
            blockExecute(result)
        }
    }

}
