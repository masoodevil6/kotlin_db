package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.manager.DatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base.core.data_base.DatabasePlatformConfig
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.sql.Connection

/** Runs one callback on a single owned JDBC connection and finalizes it as a transaction. */
class OperationTransactionExecutor(
    private val connectionFactory: () -> Connection,
    private val databasePlatformConfig: DatabasePlatformConfig,
) {
    constructor() : this(DatabaseContext.legacyDefault())

    constructor(databaseContext: DatabaseContext) : this(
        databaseContext::openConnection,
        databaseContext.platformConfig,
    )

    /** Compatibility constructor for callers that supply their own Connection factory. */
    @Suppress("DEPRECATION")
    constructor(connectionFactory: () -> Connection) : this(
        connectionFactory,
        DatabaseContext.legacyDefault().platformConfig,
    )
    fun <T> transaction(
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = transaction(OperationExecutionCoordinator(), block)

    /** Runs this transaction within the supplied family-specific root execution lineage. */
    fun <T> transaction(
        coordinator: OperationExecutionCoordinator,
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val nestedFailure = OperationScopeGuard.enter()
        if (nestedFailure != null) return ExecuteResult.Failure(nestedFailure)

        val startFailure = coordinator.tryStartScope(
            IllegalStateException("Nested transactions are not supported"),
        )
        if (startFailure != null) {
            OperationScopeGuard.exit()
            return ExecuteResult.Failure(startFailure)
        }

        var operationScope: OperationExecutionScope? = null
        try {
        val connection = try {
            connectionFactory()
        } catch (error: Error) {
            throw error
        } catch (exception: Exception) {
            return ExecuteResult.Failure(exception)
        }

        try {
            connection.autoCommit = false
        } catch (error: Error) {
            val fatal = closeAfterFailure(connection, error)
            throw fatal ?: error
        } catch (exception: Exception) {
            val fatal = closeAfterFailure(connection, exception)
            if (fatal != null) throw fatal
            return ExecuteResult.Failure(exception)
        }

        val scope = OperationExecutionScope(connection, databasePlatformConfig)
        operationScope = scope
        OperationScopeGuard.activate(scope)
        coordinator.activate(scope)
        var callbackResult: ExecuteResult<T>? = null
        try {
            callbackResult = block(scope)
        } catch (error: Error) {
            scope.recordFatal(error)
        } catch (exception: Exception) {
            scope.recordFailure(exception)
        } finally {
            coordinator.seal(scope)
        }

        val returnedFailure = callbackResult as? ExecuteResult.Failure
        val primary = scope.firstFailure ?: returnedFailure?.exception
        val fatal = scope.fatalError ?: (primary as? Error)
        if (fatal != null) {
            val cleanupError = rollbackAndClose(connection, fatal)
            throw cleanupError ?: fatal
        }

        if (scope.firstFailure != null) {
            val scopeFailure = requireNotNull(scope.firstFailure)
            val cleanupError = rollbackAndClose(connection, scopeFailure)
            if (cleanupError != null) throw cleanupError
            return ExecuteResult.Failure(scopeFailure)
        }

        if (returnedFailure != null) {
            val cleanupError = rollbackAndClose(connection, returnedFailure.exception)
            if (cleanupError != null) throw cleanupError
            return returnedFailure
        }

        val result = callbackResult
            ?: run {
                val missingResult = IllegalStateException("Transaction callback returned no result")
                val cleanupError = rollbackAndClose(connection, missingResult)
                if (cleanupError != null) throw cleanupError
                return ExecuteResult.Failure(missingResult)
            }

        if (result is ExecuteResult.Failure) {
            val cleanupError = rollbackAndClose(connection, result.exception)
            if (cleanupError != null) throw cleanupError
            return result
        }

        try {
            connection.commit()
        } catch (error: Error) {
            val cleanupError = rollbackAndClose(connection, error)
            throw cleanupError ?: error
        } catch (exception: Exception) {
            val commitFailure = OperationTransactionCommitException(exception)
            val cleanupError = rollbackAndClose(connection, commitFailure)
            if (cleanupError != null) throw cleanupError
            return ExecuteResult.Failure(commitFailure)
        }

        try {
            connection.close()
        } catch (error: Error) {
            throw OperationTransactionCompletionError(committed = true, cause = error)
        } catch (exception: Exception) {
            return ExecuteResult.Failure(
                OperationTransactionCompletionException(committed = true, cause = exception),
            )
        }

        return result
        } finally {
            operationScope?.end()
            coordinator.finishScope()
            OperationScopeGuard.exit()
        }
    }

    private fun closeAfterFailure(connection: Connection, primary: Throwable): Error? {
        try {
            connection.close()
        } catch (failure: Error) {
            if (primary is Error) {
                addSuppressedSafely(primary, failure)
                return primary
            }
            addSuppressedSafely(failure, primary)
            return failure
        } catch (failure: Exception) {
            addSuppressedSafely(primary, failure)
        }
        return primary as? Error
    }

    /** Returns a fatal cleanup Error while retaining the original failure as primary where possible. */
    private fun rollbackAndClose(connection: Connection, primary: Throwable): Error? {
        var fatal: Error? = primary as? Error
        try {
            connection.rollback()
        } catch (failure: Error) {
            if (fatal == null) {
                fatal = failure
                addSuppressedSafely(failure, primary)
            } else {
                addSuppressedSafely(fatal, failure)
            }
        } catch (failure: Exception) {
            addSuppressedSafely(primary, failure)
        }

        try {
            connection.close()
        } catch (failure: Error) {
            if (fatal == null) {
                fatal = failure
                addSuppressedSafely(failure, primary)
            } else {
                addSuppressedSafely(fatal, failure)
            }
        } catch (failure: Exception) {
            addSuppressedSafely(fatal ?: primary, failure)
        }
        return fatal
    }

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary) primary.addSuppressed(secondary)
    }
}

class OperationTransactionCommitException(cause: Throwable) :
    IllegalStateException(
        "JDBC commit failed; rollback was attempted and the commit outcome may be unknown",
        cause,
    )

class OperationTransactionCompletionException(
    val committed: Boolean,
    cause: Exception,
) : IllegalStateException(
    if (committed) {
        "The transaction committed, but closing its connection failed; do not retry as an uncommitted transaction"
    } else {
        "The transaction did not commit, but closing its connection failed"
    },
    cause,
)

class OperationTransactionCompletionError(
    val committed: Boolean,
    cause: Error,
) : Error(
    if (committed) {
        "The transaction committed, but closing its connection failed; do not retry as an uncommitted transaction"
    } else {
        "The transaction did not commit, but closing its connection failed"
    },
    cause,
)
