package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.events.DatabaseEventListener
import gog.my_project.data_base.core.events.DatabaseWatchSubscription
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Concrete handle for the capabilities configured for one database key. Consumers may depend on
 * [DatabaseHandleApi] while existing code can continue using this concrete return type.
 */
class DatabaseHandle private constructor(
    override val key: DatabaseKey,
    private val operationRunner: DatabaseHandleOperationRunner,
    private val registrationAttributes: Map<DatabaseRegistrationAttribute<*>, Any>,
) : DatabaseHandleApi, DatabaseHandleAttributes {
    private val watchListeners = CopyOnWriteArrayList<DatabaseEventListener>()

    /** Internal seam for direct handle lifecycle tests. */
    internal constructor(
        key: DatabaseKey,
        resolveContext: () -> DatabaseContext,
    ) : this(
        key,
        object : DatabaseHandleOperationRunner {
            override fun <T> run(block: (() -> DatabaseContext) -> T): T = block(resolveContext)
        },
        emptyMap(),
    )

    override fun <T : Any> attribute(key: DatabaseRegistrationAttribute<T>): T? =
        registrationAttributes[key]?.let(key::checkedValue)

    /** Runs the block in a non-transactional Connection Scope owned by Operation. */
    override fun <T> connection(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
        withContext { context ->
            queryExecutor(context).connection(block)
        }

    /**
     * Runs operations in Operation's Transaction Scope on its managed Connection. Commit,
     * rollback, and cleanup follow the existing Operation transaction contract.
     */
    override fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
        withContext { context ->
            queryExecutor(context).transaction(block)
        }

    /** Runs a callback in Operation's managed root Connection Scope under the same Manager lease. */
    internal fun <T> runWithOperationScope(
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = withContext { context ->
        operationExecutor(context).connection(block)
    }

    /** Registers a listener on this Handle without opening a Connection. */
    fun watch(listener: DatabaseEventListener): DatabaseWatchSubscription {
        watchListeners += listener
        return HandleWatchSubscription(watchListeners, listener)
    }

    private fun queryExecutor(context: DatabaseContext): QueryBuilderExecutor =
        watchOperationListener()?.let { listener ->
            QueryBuilderExecutor(context, listener)
        } ?: QueryBuilderExecutor(context)

    private fun operationExecutor(context: DatabaseContext): OperationExecutionExecutor =
        watchOperationListener()?.let { listener ->
            OperationExecutionExecutor(context, listener)
        } ?: OperationExecutionExecutor(context)

    /** Snapshots listeners before the Operation opens its Connection. */
    private fun watchOperationListener(): OperationEventListener? {
        val snapshot = watchListeners.toList()
        if (snapshot.isEmpty()) return null
        return OperationEventListener.fromDatabaseEventListener { event ->
            snapshot.forEach { listener ->
                try {
                    listener.onEvent(event)
                } catch (_: Throwable) {
                    // One observer cannot affect the operation or prevent other observers.
                }
            }
        }
    }

    private fun <T> withContext(block: (DatabaseContext) -> ExecuteResult<T>): ExecuteResult<T> =
        try {
            operationRunner.run { resolveContext -> block(resolveContext()) }
        } catch (error: Error) {
            throw error
        } catch (exception: Exception) {
            ExecuteResult.Failure(exception)
        }

    companion object {
        internal fun managed(
            key: DatabaseKey,
            operationRunner: DatabaseHandleOperationRunner,
            registrationAttributes: Map<DatabaseRegistrationAttribute<*>, Any>,
        ): DatabaseHandle = DatabaseHandle(key, operationRunner, registrationAttributes)
    }
}

private class HandleWatchSubscription(
    private val listeners: CopyOnWriteArrayList<DatabaseEventListener>,
    private val listener: DatabaseEventListener,
) : DatabaseWatchSubscription {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.remove(listener)
        }
    }
}

/** Internal bridge so every handle capability shares one Manager admission lease. */
internal interface DatabaseHandleOperationRunner {
    fun <T> run(block: (() -> DatabaseContext) -> T): T
}
