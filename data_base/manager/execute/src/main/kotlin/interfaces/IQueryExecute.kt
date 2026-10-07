package gog.my_project.data_base.manager.execute.interfaces

import gog.my_project.data_base.core.data_base.DatabaseServerInfo
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.connection.manager.DatabaseConnection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.use

interface IQueryExecute {
    /** Reads and parses server identity at the existing database manager boundary. */
    fun getDatabaseServerInfo(blockExecute: (ExecuteResult<DatabaseServerInfo>) -> Unit) {
        executeSelect(BuiltQuery("SELECT VERSION()", mutableListOf())) { result ->
            val parsedResult: ExecuteResult<DatabaseServerInfo> = when (result) {
                is ExecuteResult.Failure -> result
                is ExecuteResult.Success -> {
                    try {
                        val rawVersion = result.result?.use { rows ->
                            if (rows.next()) rows.getString(1) else null
                        }
                        if (rawVersion == null) {
                            ExecuteResult.Failure(
                                IllegalStateException("Database did not return its server version"),
                            )
                        } else {
                            ExecuteResult.Success(DatabaseServerInfo.parse(rawVersion))
                        }
                    } catch (error: Throwable) {
                        ExecuteResult.Failure(error)
                    }
                }
            }
// Consumer callbacks are deliberately outside the catch above.
            blockExecute(parsedResult)
        }
    }

    fun executeSelect(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
    )

    fun executeUpdate(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    )

    fun executeInsert(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Long>) -> Unit,
    )

    fun executeDelete(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit,
    )

    fun executeTable(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    )

    /** Acquires the connection and invokes the operation once; operation code owns JDBC error delivery. */
    fun execute(
        builtQuery: BuiltQuery,
        blockExecute: (
            conn: Connection,
            query: String?,
            params: MutableList<SqlParameter<*>>,
            paramsName: List<String>,
            error: Throwable?,
        ) -> Unit,
    ) {
        val query = builtQuery.getReadyQuery()
        val params = builtQuery.params
        // This returns occurrence order and validates mismatches before a connection is opened.
        // A null SQL input retains its legacy post-connection failure path.
        val paramsNames = builtQuery.getListParamNames()

        val connection = DatabaseConnection().build()
        connection.use { conn ->
            if (query == null) {
                blockExecute(
                    conn,
                    null,
                    params,
                    paramsNames,
                    IllegalArgumentException("BuiltQuery query must not be null"),
                )
            } else {
                // Do not wrap this invocation in a SQLException catch: user callback exceptions
                // must not be mistaken for JDBC failures and cause callback re-entry.
                blockExecute(conn, query, params, paramsNames, null)
            }
        }
    }

    /** Maps textual placeholder occurrences to JDBC indexes and delegates value binding. */
    fun readyParamsInQuery(
        ps: PreparedStatement?,
        params: MutableList<SqlParameter<*>>,
        paramsNames: List<String>,
    ): PreparedStatement? {
        if (ps == null) return null

        val parametersByName = linkedMapOf<String, SqlParameter<*>>()
        for (parameter in params) {
            val existing = parametersByName[parameter.name]
            if (existing == null) {
                parametersByName[parameter.name] = parameter
            } else {
                require(existing.value == parameter.value) {
                    "Conflicting values supplied for parameter '${parameter.name}'"
                }
                require(existing.sqlType == parameter.sqlType) {
                    "Conflicting SQL types supplied for parameter '${parameter.name}'"
                }
            }
        }

        for ((index, parameterName) in paramsNames.withIndex()) {
            val parameter = parametersByName[parameterName]
                ?: throw IllegalArgumentException("Placeholder ':$parameterName' has no matching parameter")
            parameter.bind(ps, index + 1)
        }

        return ps
    }

}