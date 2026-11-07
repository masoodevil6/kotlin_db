package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
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

        var connection: Connection? = null
        var connectionClosed = false
        var scope: OperationExecutionScope? = null
        var transactionStarted = false

        fun closeOnce(primary: Throwable?): Throwable? {
            if (connectionClosed) return primary
            connectionClosed = true
            return closeConnection(requireNotNull(connection), primary)
        }

        fun rollbackAndClose(primary: Throwable): Error? = rollbackAndClose(
            requireNotNull(connection),
            primary,
            ::closeOnce,
        )

        try {
            connection = try {
                connectionFactory()
            } catch (failure: Throwable) {
                if (failure is Error) throw failure
                return ExecuteResult.Failure(failure)
            }

            try {
                connection.autoCommit = false
            } catch (failure: Throwable) {
                val closeFailure = closeOnce(failure)
                if (failure is Error) throw failure
                if (closeFailure is Error) throw closeFailure
                return ExecuteResult.Failure(failure)
            }
            transactionStarted = true

            val activeScope = OperationExecutionScope(connection, databasePlatformConfig)
            scope = activeScope
            coordinator.prepareScope(activeScope)
            OperationScopeGuard.activate(activeScope)
            coordinator.activateOwned(activeScope)

            var callbackResult: ExecuteResult<T>? = null
            try {
                callbackResult = block(activeScope)
                activeScope.recordResult(callbackResult)
            } catch (failure: Throwable) {
                if (failure is Error) activeScope.recordFatal(failure) else activeScope.recordFailure(failure)
            }

            try {
                coordinator.sealOwned(activeScope)
            } catch (sealFailure: Throwable) {
                activeScope.recordFailure(sealFailure)
                if (sealFailure is Error) activeScope.recordFatal(sealFailure)
                coordinator.abortScope(activeScope, sealFailure)
            }

            val returnedFailure = callbackResult as? ExecuteResult.Failure
            val primary = activeScope.firstFailure ?: returnedFailure?.exception
            val fatal = activeScope.fatalError ?: (primary as? Error)
            if (fatal != null) {
                val cleanupError = rollbackAndClose(fatal)
                throw cleanupError ?: fatal
            }

            if (primary != null) {
                val cleanupError = rollbackAndClose(primary)
                if (cleanupError != null) throw cleanupError
                if (returnedFailure?.exception === primary) return returnedFailure
                return ExecuteResult.Failure(primary)
            }

            val result = callbackResult ?: run {
                val missingResult = IllegalStateException("Transaction callback returned no result")
                val cleanupError = rollbackAndClose(missingResult)
                if (cleanupError != null) throw cleanupError
                return ExecuteResult.Failure(missingResult)
            }

            try {
                connection.commit()
            } catch (commitFailure: Throwable) {
                val reportedFailure = if (commitFailure is Exception) {
                    OperationTransactionCommitException(commitFailure)
                } else {
                    commitFailure
                }
                val cleanupError = rollbackAndClose(reportedFailure)
                if (cleanupError != null) throw cleanupError
                if (commitFailure is Error) throw commitFailure
                return ExecuteResult.Failure(reportedFailure)
            }

            val closeFailure = closeOnce(null)
            when (closeFailure) {
                null -> Unit
                is Error -> throw OperationTransactionCompletionError(committed = true, cause = closeFailure)
                is Exception -> return ExecuteResult.Failure(
                    OperationTransactionCompletionException(committed = true, cause = closeFailure),
                )
                else -> return ExecuteResult.Failure(closeFailure)
            }
            return result
        } catch (failure: Throwable) {
            if (connection != null && !connectionClosed) {
                val cleanupError = if (transactionStarted) {
                    rollbackAndClose(failure)
                } else {
                    closeOnce(failure) as? Error
                }
                if (failure is Error) throw failure
                if (cleanupError != null) throw cleanupError
                return ExecuteResult.Failure(failure)
            }
            if (failure is Error) throw failure
            return ExecuteResult.Failure(failure)
        } finally {
            scope?.endForLifecycle()
            coordinator.finishOwnedScope()
            OperationScopeGuard.exit()
        }
    }

    private fun closeConnection(connection: Connection, primary: Throwable?): Throwable? {
        val closeFailure = try {
            connection.close()
            return primary
        } catch (failure: Throwable) {
            failure
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

    /** Returns a fatal cleanup Error while retaining the original failure as primary where possible. */
    private fun rollbackAndClose(
        connection: Connection,
        primary: Throwable,
        closeOnce: (Throwable?) -> Throwable?,
    ): Error? {
        var fatal: Error? = primary as? Error
        try {
            connection.rollback()
        } catch (failure: Throwable) {
            if (failure is Error) {
                if (fatal == null) {
                    fatal = failure
                    addSuppressedSafely(failure, primary)
                } else {
                    addSuppressedSafely(fatal, failure)
                }
            } else {
                addSuppressedSafely(fatal ?: primary, failure)
            }
        }

        val closeFailure = closeOnce(fatal ?: primary)
        if (closeFailure is Error) {
            if (fatal == null) {
                fatal = closeFailure
                addSuppressedSafely(closeFailure, primary)
            } else if (closeFailure !== fatal) {
                addSuppressedSafely(fatal, closeFailure)
            }
        } else if (closeFailure != null && closeFailure !== primary) {
            addSuppressedSafely(fatal ?: primary, closeFailure)
        }
        return fatal
    }

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary && primary.suppressed.none { it === secondary }) {
            primary.addSuppressed(secondary)
        }
    }
}

class OperationTransactionCommitException(cause: Exception) :
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
