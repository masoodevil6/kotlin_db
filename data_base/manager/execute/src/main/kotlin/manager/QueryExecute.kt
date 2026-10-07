package gog.my_project.data_base.manager.execute.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import kotlin.use

class QueryExecute : IQueryExecute {
    override fun executeSelect(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
    ) {
        execute(builtQuery) { conn, query, params, paramsNames, error ->
            if (error != null) {
                blockExecute(ExecuteResult.Failure(error))
                return@execute
            }

            val statement = try {
                conn.prepareStatement(requireNotNull(query))
            } catch (exception: SQLException) {
                blockExecute(ExecuteResult.Failure(exception))
                return@execute
            }

            statement.use { stmt ->
                val result: ExecuteResult<ResultSet> = try {
                    val prepared = requireNotNull(readyParamsInQuery(stmt, params, paramsNames))
                    ExecuteResult.Success(prepared.executeQuery())
                } catch (exception: SQLException) {
                    ExecuteResult.Failure(exception)
                }
                // The ResultSet is live here. Consumer exceptions are outside the JDBC catch.
                blockExecute(result)
            }
        }
    }

    override fun executeInsert(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Long>) -> Unit,
    ) {
        execute(builtQuery) { conn, query, params, paramsNames, error ->
            if (error != null) {
                blockExecute(ExecuteResult.Failure(error))
                return@execute
            }

            val statement = try {
                conn.prepareStatement(requireNotNull(query), Statement.RETURN_GENERATED_KEYS)
            } catch (exception: SQLException) {
                blockExecute(ExecuteResult.Failure(exception))
                return@execute
            }

            statement.use { stmt ->
                val result: ExecuteResult<Long> = try {
                    val prepared = requireNotNull(readyParamsInQuery(stmt, params, paramsNames))
                    val affectedRows: Int? = prepared.executeUpdate()
                    when {
                        affectedRows == null -> ExecuteResult.Failure(
                            IllegalStateException("INSERT did not report an affected-row count"),
                        )
                        affectedRows <= 0 -> ExecuteResult.Failure(
                            IllegalStateException("INSERT did not affect any rows"),
                        )
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
                } catch (exception: SQLException) {
                    ExecuteResult.Failure(exception)
                }
                // Generated keys have been closed before the one terminal callback.
                blockExecute(result)
            }
        }
    }

    override fun executeUpdate(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        execute(builtQuery) { conn, query, params, paramsNames, error ->
            if (error != null) {
                blockExecute(ExecuteResult.Failure(error))
                return@execute
            }

            val statement = try {
                conn.prepareStatement(requireNotNull(query))
            } catch (exception: SQLException) {
                blockExecute(ExecuteResult.Failure(exception))
                return@execute
            }

            statement.use { stmt ->
                val result: ExecuteResult<Int> = try {
                    val prepared = requireNotNull(readyParamsInQuery(stmt, params, paramsNames))
                    ExecuteResult.Success(prepared.executeUpdate())
                } catch (exception: SQLException) {
                    ExecuteResult.Failure(exception)
                }
                blockExecute(result)
            }
        }
    }

    override fun executeDelete(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    ) {
        execute(builtQuery) { conn, query, params, paramsNames, error ->
            if (error != null) {
                blockExecute(ExecuteResult.Failure(error))
                return@execute
            }

            val statement = try {
                conn.prepareStatement(requireNotNull(query))
            } catch (exception: SQLException) {
                blockExecute(ExecuteResult.Failure(exception))
                return@execute
            }

            statement.use { stmt ->
                val result: ExecuteResult<Int> = try {
                    val prepared = requireNotNull(readyParamsInQuery(stmt, params, paramsNames))
                    ExecuteResult.Success(prepared.executeUpdate())
                } catch (exception: SQLException) {
                    ExecuteResult.Failure(exception)
                }
                blockExecute(result)
            }
        }
    }

    override fun executeTable(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        execute(builtQuery) { conn, query, params, paramsNames, error ->
            if (error != null) {
                blockExecute(ExecuteResult.Failure(error))
                return@execute
            }

            val statement = try {
                conn.prepareStatement(requireNotNull(query))
            } catch (exception: SQLException) {
                blockExecute(ExecuteResult.Failure(exception))
                return@execute
            }

            statement.use { stmt ->
                val result: ExecuteResult<Boolean> = try {
                    val prepared = requireNotNull(readyParamsInQuery(stmt, params, paramsNames))
                    prepared.execute() // The JDBC boolean is intentionally not exposed.
                    ExecuteResult.Success(true)
                } catch (exception: SQLException) {
                    ExecuteResult.Failure(exception)
                }
                blockExecute(result)
            }
        }
    }
}
