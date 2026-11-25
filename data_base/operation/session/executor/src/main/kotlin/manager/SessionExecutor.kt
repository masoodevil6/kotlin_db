package gog.my_project.data_base_session.executor.manager

import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.ast.enums.SessionCommandKind
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.dialect.data_class.RenderedSessionCommand
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.executor.exceptions.SessionExecutionException
import gog.my_project.data_base_session.executor.exceptions.SessionProtocolException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement

class SessionExecutor {
    private val dialectSelector = SessionDialectSelector()

    fun acquire(
        ast: IAcquireSessionAdvisoryLockAst,
        connection: Connection,
        dialect: SessionDialect,
    ): AcquireSessionLockResult {
        val command = render(ast, dialect, SessionCommandKind.ACQUIRE_SESSION_ADVISORY_LOCK)
        return when (readStatus(command, connection, isAcquire = true)) {
            1 -> AcquireSessionLockResult.Acquired
            0 -> AcquireSessionLockResult.TimedOut
            null -> throw SessionProtocolException("GET_LOCK returned SQL NULL.")
            else -> error("readStatus only returns 0, 1, or null")
        }
    }

    fun release(
        ast: IReleaseSessionAdvisoryLockAst,
        connection: Connection,
        dialect: SessionDialect,
    ): ReleaseSessionLockResult {
        val command = render(ast, dialect, SessionCommandKind.RELEASE_SESSION_ADVISORY_LOCK)
        return when (readStatus(command, connection, isAcquire = false)) {
            1 -> ReleaseSessionLockResult.Released
            0, null -> ReleaseSessionLockResult.NotOwned
            else -> error("readStatus only returns 0, 1, or null")
        }
    }

    private fun render(
        ast: ISessionCommandAst,
        dialect: SessionDialect,
        expectedKind: SessionCommandKind,
    ): RenderedSessionCommand {
        val command = dialectSelector.select(dialect).render(ast)
        if (command.kind != expectedKind) {
            throw SessionRenderException.InvalidAst(
                "Expected $expectedKind but renderer produced ${command.kind}.",
            )
        }
        return command
    }

    /** Returns the exact integer status, preserving SQL NULL for operation-specific handling. */
    private fun readStatus(
        command: RenderedSessionCommand,
        connection: Connection,
        isAcquire: Boolean,
    ): Int? {
        try {
            connection.prepareStatement(command.sql).use { statement ->
                bind(statement, command.parameters)

                if (!statement.execute()) {
                    val updateCount = statement.updateCount
                    val detail = if (updateCount == -1) {
                        "no initial ResultSet was returned"
                    } else {
                        "an unexpected initial update count was returned ($updateCount)"
                    }
                    throw SessionProtocolException("${command.kind}: $detail.")
                }

                val resultSet = statement.resultSet
                    ?: throw SessionProtocolException("${command.kind}: ResultSet was missing.")

                resultSet.use { rows ->
                    val metadata = rows.metaData
                        ?: throw SessionProtocolException("${command.kind}: ResultSet metadata was missing.")
                    val columnCount = metadata.columnCount
                    if (columnCount != 1) {
                        throw SessionProtocolException(
                            "${command.kind}: expected exactly one column, received $columnCount.",
                        )
                    }
                    if (!rows.next()) {
                        throw SessionProtocolException("${command.kind}: ResultSet contained no row.")
                    }

                    val value = rows.getObject(1)
                    if (rows.next()) {
                        throw SessionProtocolException("${command.kind}: ResultSet contained more than one row.")
                    }

                    if (statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)) {
                        throw SessionProtocolException("${command.kind}: an additional ResultSet was returned.")
                    }
                    val followingUpdateCount = statement.updateCount
                    if (followingUpdateCount != -1) {
                        throw SessionProtocolException(
                            "${command.kind}: an unexpected additional update count was returned " +
                                "($followingUpdateCount).",
                        )
                    }

                    return exactStatus(value, command.kind, isAcquire)
                }
            }
        } catch (failure: SessionProtocolException) {
            throw failure
        } catch (failure: SQLException) {
            throw SessionExecutionException("JDBC execution failed for ${command.kind}.", failure)
        }
    }

    private fun bind(
        statement: PreparedStatement,
        parameters: List<SessionCommandParameter>,
    ) {
        parameters.forEachIndexed { index, parameter ->
            val jdbcIndex = index + 1
            if (parameter.value == null) {
                statement.setNull(jdbcIndex, parameter.sqlType)
            } else {
                statement.setObject(jdbcIndex, parameter.value, parameter.sqlType)
            }
        }
    }

    private fun exactStatus(
        value: Any?,
        kind: SessionCommandKind,
        isAcquire: Boolean,
    ): Int? {
        if (value == null) {
            if (!isAcquire) return null
            throw SessionProtocolException("$kind returned SQL NULL.")
        }

        val status = when (value) {
            is Byte -> value.toInt()
            is Short -> value.toInt()
            is Int -> value
            is Long -> {
                if (value !in 0L..1L) {
                    throw SessionProtocolException("$kind returned unexpected integer value '$value'.")
                }
                value.toInt()
            }
            else -> throw SessionProtocolException(
                "$kind returned unsupported value type ${value::class.qualifiedName}.",
            )
        }

        if (status !in 0..1) {
            throw SessionProtocolException("$kind returned unexpected integer value '$status'.")
        }
        return status
    }
}
