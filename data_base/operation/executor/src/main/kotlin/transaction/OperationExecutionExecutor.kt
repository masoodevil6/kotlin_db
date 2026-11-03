package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base.manager.connection.manager.DatabaseConnection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.Connection

/** Owns Connection and Transaction scope lifecycles for an execution lineage. */
class OperationExecutionExecutor(
    private val connectionFactory: () -> Connection = { DatabaseConnection().build() },
) {
    fun <T> connection(
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = connection(OperationExecutionCoordinator(), block)

    /** Executes a non-transactional scope using the supplied root lineage coordinator. */
    fun <T> connection(
        coordinator: OperationExecutionCoordinator,
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val nestedFailure = OperationScopeGuard.enter()
        if (nestedFailure != null) return ExecuteResult.Failure(nestedFailure)

        val startFailure = coordinator.tryStartScope(
            IllegalStateException("Nested connection scopes are not supported"),
        )
        if (startFailure != null) {
            OperationScopeGuard.exit()
            return ExecuteResult.Failure(startFailure)
        }

        var scope: OperationExecutionScope? = null
        try {
            val connection = try {
                connectionFactory()
            } catch (error: Error) {
                throw error
            } catch (exception: Exception) {
                return ExecuteResult.Failure(exception)
            }

            val activeScope = try {
                OperationExecutionScope(connection).also {
                    scope = it
                    OperationScopeGuard.activate(it)
                    coordinator.activate(it)
                }
            } catch (error: Error) {
                closeConnection(connection, error)
                throw error
            } catch (exception: Exception) {
                val closeFailure = closeConnection(connection, exception)
                if (closeFailure is Error) throw closeFailure
                return ExecuteResult.Failure(exception)
            }

            var callbackResult: ExecuteResult<T>? = null
            var callbackException: Exception? = null
            var callbackError: Error? = null
            try {
                callbackResult = block(activeScope)
            } catch (error: Error) {
                callbackError = error
                activeScope.recordFatal(error)
            } catch (exception: Exception) {
                callbackException = exception
            } finally {
                coordinator.seal(activeScope)
            }

            val fatalError = activeScope.fatalError ?: callbackError
            if (fatalError != null) {
                closeConnection(connection, fatalError)
                throw fatalError
            }

            val scopedFailure = activeScope.firstFailure
            if (scopedFailure != null) {
                if (callbackException != null && callbackException !== scopedFailure) {
                    addSuppressedSafely(scopedFailure, callbackException)
                }
                val closeOutcome = closeConnection(connection, scopedFailure)
                if (closeOutcome is Error) throw closeOutcome
                return ExecuteResult.Failure(scopedFailure)
            }

            if (callbackException != null) {
                val closeOutcome = closeConnection(connection, callbackException)
                if (closeOutcome is Error) throw closeOutcome
                return ExecuteResult.Failure(callbackException)
            }

            val result = callbackResult ?: run {
                val missingResult = IllegalStateException("Connection-scope callback returned no result")
                val closeOutcome = closeConnection(connection, missingResult)
                if (closeOutcome is Error) throw closeOutcome
                return ExecuteResult.Failure(missingResult)
            }

            if (result is ExecuteResult.Failure) {
                val closeOutcome = closeConnection(connection, result.exception)
                if (closeOutcome is Error) throw closeOutcome
                return result
            }

            val closeFailure = closeConnection(connection, null)
            if (closeFailure is Error) throw closeFailure
            if (closeFailure is Exception) {
                return ExecuteResult.Failure(
                    IllegalStateException(
                        "Connection scope completed without commit or rollback, but closing its connection failed; prior changes may already be durable, so do not retry blindly",
                        closeFailure,
                    ),
                )
            }
            return result
        } finally {
            scope?.end()
            coordinator.finishScope()
            OperationScopeGuard.exit()
        }
    }

    fun <T> transaction(
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = transaction(OperationExecutionCoordinator(), block)

    /** Executes a Transaction Scope using the same root lineage as its family-specific adapter. */
    fun <T> transaction(
        coordinator: OperationExecutionCoordinator,
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = OperationTransactionExecutor(connectionFactory).transaction(coordinator, block)

    private fun closeConnection(connection: Connection, primary: Throwable?): Throwable? {
        val closeFailure = try {
            connection.close()
            return primary
        } catch (error: Error) {
            error
        } catch (exception: Exception) {
            exception
        }

        if (primary is Error) {
            addSuppressedSafely(primary, closeFailure)
            return primary
        }
        if (closeFailure is Error) {
            if (primary != null) addSuppressedSafely(closeFailure, primary)
            return closeFailure
        }
        if (primary != null) {
            addSuppressedSafely(primary, closeFailure)
            return primary
        }
        return closeFailure
    }

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary) primary.addSuppressed(secondary)
    }
}
