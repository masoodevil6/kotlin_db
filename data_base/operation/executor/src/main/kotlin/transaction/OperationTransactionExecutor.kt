package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.executor.events.OperationEvent
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_operation.executor.events.OperationEventType
import gog.my_project.data_base_operation.executor.events.publish
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.sql.Connection

/** Runs one callback on a single owned JDBC connection and finalizes it as a transaction. */
class OperationTransactionExecutor private constructor(
    private val connectionFactory: () -> Connection,
    private val databasePlatformConfig: DatabasePlatformConfig,
    private val databaseIdentity: DatabaseIdentity?,
    private val eventListener: OperationEventListener?,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit,
) {

    /** Preserves the established public connection-factory and platform constructor. */
    constructor(
        connectionFactory: () -> Connection,
        databasePlatformConfig: DatabasePlatformConfig,
    ) : this(connectionFactory, databasePlatformConfig, null, null, Unit)

    /** Operation-owned path for carrying the identity associated with a DatabaseContext. */
    internal constructor(
        connectionFactory: () -> Connection,
        databasePlatformConfig: DatabasePlatformConfig,
        databaseIdentity: DatabaseIdentity?,
    ) : this(connectionFactory, databasePlatformConfig, databaseIdentity, null, Unit)

    /** Internal propagation path used by OperationExecutionExecutor. */
    internal constructor(
        connectionFactory: () -> Connection,
        databasePlatformConfig: DatabasePlatformConfig,
        databaseIdentity: DatabaseIdentity?,
        eventListener: OperationEventListener?,
    ) : this(connectionFactory, databasePlatformConfig, databaseIdentity, eventListener, Unit)

    constructor() : this(DatabaseContext.legacyDefault())

    constructor(databaseContext: DatabaseContext) : this(
        databaseContext::openConnection,
        databaseContext.platformConfig,
        databaseContext.databaseIdentity,
        null,
        Unit,
    )

    /** Adds a read-only observer to the lifecycle owned by this Operation executor. */
    constructor(databaseContext: DatabaseContext, eventListener: OperationEventListener) : this(
        databaseContext::openConnection,
        databaseContext.platformConfig,
        databaseContext.databaseIdentity,
        eventListener,
        Unit,
    )

    /** Compatibility constructor for callers that supply their own Connection factory. */
    @Suppress("DEPRECATION")
    constructor(connectionFactory: () -> Connection) : this(
        connectionFactory,
        DatabaseContext.legacyDefault().platformConfig,
        null,
        null,
        Unit,
    )

    /** Compatibility constructor counterpart for caller-supplied Connection providers. */
    @Suppress("DEPRECATION")
    constructor(connectionFactory: () -> Connection, eventListener: OperationEventListener) : this(
        connectionFactory,
        DatabaseContext.legacyDefault().platformConfig,
        null,
        eventListener,
        Unit,
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
                requireNotNull(connectionFactory()).also {
                    eventListener.publish(OperationEvent(OperationEventType.CONNECTION_OPENED))
                }
            } catch (failure: Throwable) {
                eventListener.publish(OperationEvent(OperationEventType.CONNECTION_OPEN_FAILED, error = failure))
                if (failure is Error) throw failure
                return ExecuteResult.Failure(failure)
            }

            try {
                connection.autoCommit = false
                transactionStarted = true
                eventListener.publish(OperationEvent(OperationEventType.TRANSACTION_STARTED))
            } catch (failure: Throwable) {
                eventListener.publish(OperationEvent(OperationEventType.TRANSACTION_START_FAILED, error = failure))
                val closeFailure = closeOnce(failure)
                if (failure is Error) throw failure
                if (closeFailure is Error) throw closeFailure
                return ExecuteResult.Failure(failure)
            }

            val activeScope = OperationExecutionScope(connection, databasePlatformConfig, databaseIdentity, eventListener)
            scope = activeScope
            coordinator.prepareScope(activeScope)
            OperationScopeGuard.activateTransaction(activeScope)
            coordinator.activateOwned(activeScope)

            return executeActiveTransaction(
                connection = requireNotNull(connection),
                scope = activeScope,
                block = block,
                closeOnce = ::closeOnce,
                autoCommitToRestore = null,
                sealScope = { coordinator.sealOwned(activeScope) },
                onSealFailure = { sealFailure -> coordinator.abortScope(activeScope, sealFailure) },
            )
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
            eventListener.publish(OperationEvent(OperationEventType.CONNECTION_CLOSED))
            return primary
        } catch (failure: Throwable) {
            eventListener.publish(OperationEvent(OperationEventType.CONNECTION_CLOSE_FAILED, error = failure))
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

    private fun rollbackAndClose(
        connection: Connection,
        primary: Throwable,
        closeOnce: (Throwable?) -> Throwable?,
    ): Error? = rollbackAndFinalize(
        connection = connection,
        primary = primary,
        closeOnce = closeOnce,
        autoCommitToRestore = null,
        eventListener = eventListener,
    )

    internal companion object {
        /** Entry point used by a managed Connection Scope to borrow its owned Connection. */
        internal fun <T> transactionOn(
            parentScope: OperationExecutionScope,
            block: (transactionScope: OperationExecutionScope) -> ExecuteResult<T>,
        ): ExecuteResult<T> {
            val parentRejection = parentScope.rejectOperation()
            if (parentRejection != null) return ExecuteResult.Failure(parentRejection)

            val connection = parentScope.lifecycleConnection()
            val transactionScope = OperationExecutionScope(
                connection,
                parentScope.databasePlatformConfig,
                parentScope.databaseIdentity,
                parentScope.eventListener,
            )
            val admissionFailure = OperationScopeGuard.enterChildTransaction(parentScope, transactionScope)
            if (admissionFailure != null) {
                return ExecuteResult.Failure(parentScope.recordFailure(admissionFailure))
            }

            var result: ExecuteResult<T>? = null
            var thrown: Throwable? = null
            var autoCommit: Boolean? = null
            try {
                autoCommit = try {
                    connection.autoCommit
                } catch (failure: Throwable) {
                    transactionScope.eventListener.publish(
                        OperationEvent(OperationEventType.TRANSACTION_START_FAILED, error = failure),
                    )
                    transactionScope.recordFailure(failure)
                    result = ExecuteResult.Failure(failure)
                    if (failure is Error) thrown = failure
                    null
                }

                if (result == null && autoCommit == false) {
                    val failure = IllegalStateException(
                        "Cannot start a child transaction while the parent Connection is already transactional; nested transactions and savepoints are not supported",
                    )
                    transactionScope.eventListener.publish(
                        OperationEvent(OperationEventType.TRANSACTION_START_FAILED, error = failure),
                    )
                    transactionScope.recordFailure(failure)
                    result = ExecuteResult.Failure(failure)
                }

                if (result == null) {
                    try {
                        connection.autoCommit = false
                        transactionScope.eventListener.publish(
                            OperationEvent(OperationEventType.TRANSACTION_STARTED),
                        )
                    } catch (beginFailure: Throwable) {
                        transactionScope.eventListener.publish(
                            OperationEvent(OperationEventType.TRANSACTION_START_FAILED, error = beginFailure),
                        )
                        transactionScope.recordFailure(beginFailure)
                        val cleanupError = rollbackAndFinalize(
                            connection = connection,
                            primary = beginFailure,
                            closeOnce = { primary -> primary },
                            autoCommitToRestore = autoCommit,
                            eventListener = transactionScope.eventListener,
                        )
                        if (cleanupError != null) {
                            thrown = cleanupError
                        } else if (beginFailure is Error) {
                            thrown = beginFailure
                        } else {
                            result = ExecuteResult.Failure(beginFailure)
                        }
                    }
                }

                if (result == null && thrown == null) {
                    result = executeActiveTransaction(
                        connection = connection,
                        scope = transactionScope,
                        block = block,
                        closeOnce = { primary -> primary },
                        autoCommitToRestore = autoCommit,
                        sealScope = transactionScope::endForLifecycle,
                    )
                }
            } catch (failure: Throwable) {
                thrown = failure
            } finally {
                transactionScope.endForLifecycle()
                OperationScopeGuard.exitChildTransaction(parentScope, transactionScope)
            }

            if (thrown != null) {
                parentScope.recordFailure(thrown!!)
                if (thrown is Error) parentScope.recordFatal(thrown as Error)
                throw thrown!!
            }

            val completed = result ?: ExecuteResult.Failure(
                IllegalStateException("Child transaction completed without a result"),
            )
            // The parent sees the child outcome only after rollback/commit and state restoration finish.
            parentScope.recordResult(completed)
            return completed
        }

        private fun <T> executeActiveTransaction(
            connection: Connection,
            scope: OperationExecutionScope,
            block: (OperationExecutionScope) -> ExecuteResult<T>,
            closeOnce: (Throwable?) -> Throwable?,
            autoCommitToRestore: Boolean?,
            sealScope: () -> Unit,
            onSealFailure: (Throwable) -> Unit = {},
        ): ExecuteResult<T> {
            var callbackResult: ExecuteResult<T>? = null
            try {
                callbackResult = block(scope)
                scope.recordResult(callbackResult)
            } catch (failure: Throwable) {
                if (failure is Error) scope.recordFatal(failure) else scope.recordFailure(failure)
            }

            try {
                sealScope()
            } catch (sealFailure: Throwable) {
                scope.recordFailure(sealFailure)
                if (sealFailure is Error) scope.recordFatal(sealFailure)
                onSealFailure(sealFailure)
            }

            val returnedFailure = callbackResult as? ExecuteResult.Failure
            val primary = scope.firstFailure ?: returnedFailure?.exception
            val fatal = scope.fatalError ?: (primary as? Error)
            if (fatal != null) {
                val cleanupError = rollbackAndFinalize(connection, fatal, closeOnce, autoCommitToRestore, scope.eventListener)
                throw cleanupError ?: fatal
            }

            if (primary != null) {
                val cleanupError = rollbackAndFinalize(connection, primary, closeOnce, autoCommitToRestore, scope.eventListener)
                if (cleanupError != null) throw cleanupError
                if (returnedFailure?.exception === primary) return returnedFailure
                return ExecuteResult.Failure(primary)
            }

            val result = callbackResult ?: run {
                val missingResult = IllegalStateException("Transaction callback returned no result")
                val cleanupError = rollbackAndFinalize(connection, missingResult, closeOnce, autoCommitToRestore, scope.eventListener)
                if (cleanupError != null) throw cleanupError
                return ExecuteResult.Failure(missingResult)
            }

            try {
                connection.commit()
                scope.eventListener.publish(OperationEvent(OperationEventType.TRANSACTION_COMMITTED))
            } catch (commitFailure: Throwable) {
                scope.eventListener.publish(
                    OperationEvent(OperationEventType.TRANSACTION_COMMIT_FAILED, error = commitFailure),
                )
                val reportedFailure = if (commitFailure is Exception) {
                    OperationTransactionCommitException(commitFailure)
                } else {
                    commitFailure
                }
                val cleanupError = rollbackAndFinalize(connection, reportedFailure, closeOnce, autoCommitToRestore, scope.eventListener)
                if (cleanupError != null) throw cleanupError
                if (commitFailure is Error) throw commitFailure
                return ExecuteResult.Failure(reportedFailure)
            }

            val completionFailure = finalizeCommittedTransaction(connection, closeOnce, autoCommitToRestore)
            when (completionFailure) {
                null -> Unit
                is Error -> throw OperationTransactionCompletionError(committed = true, cause = completionFailure)
                is Exception -> return ExecuteResult.Failure(
                    OperationTransactionCompletionException(committed = true, cause = completionFailure),
                )
                else -> return ExecuteResult.Failure(completionFailure)
            }
            return result
        }

        /** Rolls back, restores borrowed Connection state, and optionally closes an owned Connection. */
        private fun rollbackAndFinalize(
            connection: Connection,
            primary: Throwable,
            closeOnce: (Throwable?) -> Throwable?,
            autoCommitToRestore: Boolean?,
            eventListener: OperationEventListener?,
        ): Error? {
            var fatal: Error? = primary as? Error

            fun retain(cleanupFailure: Throwable) {
                if (cleanupFailure is Error) {
                    if (fatal == null) {
                        fatal = cleanupFailure
                        addSuppressedSafely(cleanupFailure, primary)
                        primary.suppressed.forEach { addSuppressedSafely(cleanupFailure, it) }
                    } else {
                        addSuppressedSafely(requireNotNull(fatal), cleanupFailure)
                    }
                } else {
                    addSuppressedSafely(fatal ?: primary, cleanupFailure)
                }
            }

            try {
                connection.rollback()
                eventListener.publish(OperationEvent(OperationEventType.TRANSACTION_ROLLED_BACK))
            } catch (failure: Throwable) {
                eventListener.publish(
                    OperationEvent(OperationEventType.TRANSACTION_ROLLBACK_FAILED, error = failure),
                )
                retain(failure)
            }

            if (autoCommitToRestore != null) {
                try {
                    connection.autoCommit = autoCommitToRestore
                } catch (failure: Throwable) {
                    retain(failure)
                }
            }

            val closeFailure = closeOnce(fatal ?: primary)
            if (closeFailure is Error) {
                retain(closeFailure)
            } else if (closeFailure != null && closeFailure !== primary && closeFailure !== fatal) {
                retain(closeFailure)
            }
            return fatal
        }

        private fun finalizeCommittedTransaction(
            connection: Connection,
            closeOnce: (Throwable?) -> Throwable?,
            autoCommitToRestore: Boolean?,
        ): Throwable? {
            var primary: Throwable? = null
            if (autoCommitToRestore != null) {
                try {
                    connection.autoCommit = autoCommitToRestore
                } catch (failure: Throwable) {
                    primary = failure
                }
            }
            return closeOnce(primary)
        }

        private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
            if (primary !== secondary && primary.suppressed.none { it === secondary }) {
                primary.addSuppressed(secondary)
            }
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
