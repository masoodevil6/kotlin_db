package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.manager.DatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.executor.events.OperationEvent
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_operation.executor.events.OperationEventType
import gog.my_project.data_base_operation.executor.events.publish
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.sql.Connection

/** Owns Connection and Transaction scope lifecycles for an execution lineage. */
class OperationExecutionExecutor private constructor(
    private val connectionFactory: () -> Connection,
    private val databasePlatformConfig: DatabasePlatformConfig,
    private val databaseIdentity: DatabaseIdentity?,
    private val eventListener: OperationEventListener?,
) {
    constructor() : this(DatabaseContext.legacyDefault())

    constructor(databaseContext: DatabaseContext) : this(
        databaseContext::openConnection,
        databaseContext.platformConfig,
        databaseContext.databaseIdentity,
        null,
    )

    /** Adds a read-only observer to the lifecycle owned by this Operation executor. */
    constructor(databaseContext: DatabaseContext, eventListener: OperationEventListener) : this(
        databaseContext::openConnection,
        databaseContext.platformConfig,
        databaseContext.databaseIdentity,
        eventListener,
    )

    /** Compatibility constructor for callers that supply their own Connection factory. */
    @Suppress("DEPRECATION")
    constructor(connectionFactory: () -> Connection) : this(
        connectionFactory,
        DatabaseContext.legacyDefault().platformConfig,
        null,
        null,
    )

    /** Compatibility factory counterpart for caller-supplied Connection providers. */
    @Suppress("DEPRECATION")
    constructor(connectionFactory: () -> Connection, eventListener: OperationEventListener) : this(
        connectionFactory,
        DatabaseContext.legacyDefault().platformConfig,
        null,
        eventListener,
    )
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
        var connection: Connection? = null
        var connectionClosed = false

        fun closeOnce(primary: Throwable?): Throwable? {
            if (connectionClosed) return primary
            val ownedConnection = connection ?: return primary
            connectionClosed = true
            return closeConnection(ownedConnection, primary)
        }

        try {
            connection = try {
                requireNotNull(connectionFactory()).also {
                    eventListener.publish(OperationEvent(OperationEventType.CONNECTION_OPENED))
                }
            } catch (failure: Throwable) {
                eventListener.publish(OperationEvent(OperationEventType.CONNECTION_OPEN_FAILED, error = failure))
                throw failure
            }
            val ownedConnection = requireNotNull(connection)

            val activeScope = try {
                OperationExecutionScope(ownedConnection, databasePlatformConfig, databaseIdentity, eventListener).also {
                    scope = it
                    coordinator.prepareScope(it)
                    OperationScopeGuard.activate(it)
                    coordinator.activateOwned(it)
                }
            } catch (failure: Throwable) {
                val closeFailure = closeOnce(failure)
                if (failure is Error) throw failure
                if (closeFailure is Error) throw closeFailure
                return ExecuteResult.Failure(failure)
            }

            var callbackResult: ExecuteResult<T>? = null
            var callbackFailure: Throwable? = null
            try {
                callbackResult = block(activeScope)
                activeScope.recordResult(callbackResult)
            } catch (failure: Throwable) {
                callbackFailure = failure
                if (failure is Error) activeScope.recordFatal(failure) else activeScope.recordFailure(failure)
            }

            try {
                coordinator.sealOwned(activeScope)
            } catch (sealFailure: Throwable) {
                activeScope.recordFailure(sealFailure)
                if (sealFailure is Error) activeScope.recordFatal(sealFailure)
                coordinator.abortScope(activeScope, sealFailure)
            }

            val fatalError = activeScope.fatalError ?: (callbackFailure as? Error)
            if (fatalError != null) {
                closeOnce(fatalError)
                throw fatalError
            }

            val scopedFailure = activeScope.firstFailure
            if (scopedFailure != null) {
                val closeOutcome = closeOnce(scopedFailure)
                if (closeOutcome is Error) throw closeOutcome
                val returnedFailure = callbackResult as? ExecuteResult.Failure
                if (returnedFailure?.exception === scopedFailure) return returnedFailure
                return ExecuteResult.Failure(scopedFailure)
            }

            if (callbackFailure != null) {
                val closeOutcome = closeOnce(callbackFailure)
                if (closeOutcome is Error) throw closeOutcome
                return ExecuteResult.Failure(callbackFailure)
            }

            val result = callbackResult ?: run {
                val missingResult = IllegalStateException("Connection-scope callback returned no result")
                val closeOutcome = closeOnce(missingResult)
                if (closeOutcome is Error) throw closeOutcome
                return ExecuteResult.Failure(missingResult)
            }

            if (result is ExecuteResult.Failure) {
                val closeOutcome = closeOnce(result.exception)
                if (closeOutcome is Error) throw closeOutcome
                return result
            }

            val closeFailure = closeOnce(null)
            if (closeFailure is Error) throw closeFailure
            if (closeFailure is Exception) {
                return ExecuteResult.Failure(
                    IllegalStateException(
                        "Connection scope completed without commit or rollback, but closing its connection failed; prior changes may already be durable, so do not retry blindly",
                        closeFailure,
                    ),
                )
            }
            if (closeFailure != null) return ExecuteResult.Failure(closeFailure)
            return result
        } catch (failure: Throwable) {
            if (connection != null && !connectionClosed) {
                val closeOutcome = closeOnce(failure)
                if (failure is Error) throw failure
                if (closeOutcome is Error) throw closeOutcome
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

    fun <T> transaction(
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = transaction(OperationExecutionCoordinator(), block)

    /** Executes a Transaction Scope using the same root lineage as its family-specific adapter. */
    fun <T> transaction(
        coordinator: OperationExecutionCoordinator,
        block: (scope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = OperationTransactionExecutor(
        connectionFactory,
        databasePlatformConfig,
        databaseIdentity,
        eventListener,
    )
        .transaction(coordinator, block)

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

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary && primary.suppressed.none { it === secondary }) {
            primary.addSuppressed(secondary)
        }
    }
}
