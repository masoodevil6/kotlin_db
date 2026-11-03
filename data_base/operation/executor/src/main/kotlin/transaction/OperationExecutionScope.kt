package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base.core.data_base.DatabasePlatformConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import java.sql.Connection

/**
 * Opaque, lifecycle-neutral view of an active Operation scope for operation-family facades.
 * Implementations retain Connection ownership; callers can submit work but cannot obtain JDBC.
 */
interface OperationExecutionContext {
    /** Platform selected for the database whose scope owns this execution. */
    @Suppress("DEPRECATION")
    val databasePlatformConfig: DatabasePlatformConfig
        get() = DefaultDatabaseConfig.config.platform

    fun executeDdl(sql: String, parameters: List<OperationParameter> = emptyList()): ExecuteResult<Boolean>

    /** Latches a failure so callers cannot accidentally commit after handling its result. */
    fun <T> failed(failure: Throwable): ExecuteResult<T>
}

/** A named SQL binding passed across Operation boundaries without exposing JDBC classes. */
data class OperationParameter(
    val name: String,
    val value: Any?,
    val sqlTypeCode: Int,
)

/** Shared state for one synchronous operation scope. The scope owns no JDBC lifecycle by itself. */
class OperationExecutionScope(
    connection: Connection,
    override val databasePlatformConfig: DatabasePlatformConfig,
) : OperationExecutionContext {
    @Suppress("DEPRECATION")
    constructor(connection: Connection) : this(connection, DefaultDatabaseConfig.config.platform)

    private val ownerThread = Thread.currentThread()
    private val jdbcConnection = connection

    @Volatile
    private var active = true

    @Volatile
    var firstFailure: Throwable? = null
        private set

    @Volatile
    var fatalError: Error? = null
        private set

    /** The scope-owned connection, available only while the callback is active on its owner thread. */
    val connection: Connection
        get() {
            val rejection = rejectOperation()
            check(rejection == null) { rejection?.message ?: "Operation scope is unavailable" }
            return jdbcConnection
        }

    /** Returns and records a lifetime/thread/failure rejection before a scoped operation is run. */
    fun rejectOperation(): Throwable? = synchronized(this) {
        if (!active) {
            return@synchronized IllegalStateException("Execution scope has ended; its executor cannot run operations")
        }
        if (Thread.currentThread() !== ownerThread) {
            return@synchronized recordFailureLocked(
                IllegalStateException("Scoped operations must run on the scope callback thread"),
            )
        }
        firstFailure
    }

    fun recordResult(result: ExecuteResult<*>) {
        if (result is ExecuteResult.Failure) recordFailure(result.exception)
    }

    fun recordFailure(failure: Throwable): Throwable = synchronized(this) {
        if (!active) return@synchronized failure
        recordFailureLocked(failure)
    }

    private fun recordFailureLocked(failure: Throwable): Throwable {
        if (firstFailure == null) firstFailure = failure
        if (failure is Error && fatalError == null) fatalError = failure
        return firstFailure ?: failure
    }

    fun recordFatal(error: Error) {
        synchronized(this) {
            if (!active) return
            if (fatalError == null) fatalError = error
            if (firstFailure == null) firstFailure = error
        }
    }

    /** Seals the callback phase. Lifecycle code may still use the private connection for cleanup. */
    fun end() {
        synchronized(this) {
            active = false
        }
    }

    internal fun lifecycleConnection(): Connection = jdbcConnection

    override fun executeDdl(
        sql: String,
        parameters: List<OperationParameter>,
    ): ExecuteResult<Boolean> {
        val rejection = rejectOperation()
        if (rejection != null) return ExecuteResult.Failure(rejection)

        return try {
            var result: ExecuteResult<Boolean>? = null
            val query = BuiltQuery(
                sql,
                parameters.map { QueryParameterBinding(it.name, it.value, it.sqlTypeCode) }.toMutableList(),
            )
            QueryExecute().executeTable(jdbcConnection, query) { result = it }
            val completed = result ?: ExecuteResult.Failure(
                IllegalStateException("Scoped DDL execution completed without a result"),
            )
            recordResult(completed)
            completed
        } catch (error: Error) {
            recordFatal(error)
            throw error
        } catch (exception: Exception) {
            failed(exception)
        }
    }

    override fun <T> failed(failure: Throwable): ExecuteResult<T> =
        ExecuteResult.Failure(recordFailure(failure))
}

/** Prevents nested managed scopes across Query, Schema, and Migration on one callback thread. */
object OperationScopeGuard {
    private class State {
        var scope: OperationExecutionScope? = null
        var pendingFailure: Throwable? = null
    }

    private val current = ThreadLocal<State?>()

    /** Claims the current thread for a managed scope, or latches a nested-scope rejection. */
    fun enter(): Throwable? {
        val state = current.get()
        if (state != null) {
            val failure = IllegalStateException("Nested managed operation scopes are not supported")
            val activeScope = state.scope
            if (activeScope != null) return activeScope.recordFailure(failure)
            if (state.pendingFailure == null) state.pendingFailure = failure
            return state.pendingFailure ?: failure
        }
        current.set(State())
        return null
    }

    /** Publishes the callback scope and transfers any rejection seen while its connection was starting. */
    fun activate(scope: OperationExecutionScope) {
        val state = checkNotNull(current.get()) { "Operation scope was not entered" }
        check(state.scope == null) { "Operation scope is already active" }
        state.scope = scope
        state.pendingFailure?.let(scope::recordFailure)
        state.pendingFailure = null
    }

    fun exit() {
        current.remove()
    }
}
