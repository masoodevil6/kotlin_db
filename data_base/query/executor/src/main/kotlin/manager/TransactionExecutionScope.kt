package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.Connection
import java.util.concurrent.atomic.AtomicBoolean

/** Mutable failure and lifetime state shared by one synchronous scoped executor. */
internal class TransactionExecutionScope(
    val connection: Connection,
) {
    private val ownerThread = Thread.currentThread()

    @Volatile
    private var active = true

    @Volatile
    var firstFailure: Throwable? = null
        private set

    @Volatile
    var fatalError: Error? = null
        private set

    fun rejectQuery(): Throwable? = synchronized(this) {
        if (!active) {
            return@synchronized IllegalStateException("Execution scope has ended; its executor cannot run queries")
        }
        if (Thread.currentThread() !== ownerThread) {
            return@synchronized recordFailureLocked(
                IllegalStateException("Scoped queries must run on the scope callback thread"),
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

    fun end() {
        synchronized(this) {
            active = false
        }
    }
}

/** Detects re-entrant scopes on one QueryBuilderExecutor and exposes the active execution state. */
internal class TransactionGuard {
    private val active = ThreadLocal.withInitial { false }
    private val currentScope = ThreadLocal<TransactionExecutionScope?>()
    private val currentCoordinator = ThreadLocal<RootExecutionCoordinator?>()

    fun enter(coordinator: RootExecutionCoordinator): Boolean {
        if (active.get()) return false
        active.set(true)
        currentCoordinator.set(coordinator)
        return true
    }

    fun attach(scope: TransactionExecutionScope) {
        currentScope.set(scope)
    }

    fun recordNestedAttempt(failure: Throwable) {
        val scope = currentScope.get()
        if (scope != null) {
            scope.recordFailure(failure)
        } else {
            currentCoordinator.get()?.recordRejectedCallbackThrowable(failure)
        }
    }

    fun activeScope(): TransactionExecutionScope? = currentScope.get()

    fun exit() {
        currentScope.remove()
        currentCoordinator.remove()
        active.remove()
    }
}

/** A scope on any executor instance is still nested on the active callback thread. */
internal object TransactionGuardRegistry {
    val shared = TransactionGuard()
}

/** Coordinates ordinary root operations and managed scopes for one root executor lineage. */
internal class RootExecutionCoordinator {
    private enum class State { IDLE, STARTING, ACTIVE, FINALIZING }

    private val monitor = Any()
    private var state = State.IDLE
    private var activeRootOperations = 0
    private var activeScope: TransactionExecutionScope? = null
    private var pendingFailure: Throwable? = null
    private var pendingFatal: Error? = null

    sealed interface OperationAdmission {
        class Allowed internal constructor(internal val permit: RootOperationPermit) : OperationAdmission
        class Rejected internal constructor(internal val failure: Throwable) : OperationAdmission
    }

    /** Admits a synchronous root query only when no managed scope is starting or active. */
    fun acquireRootOperation(rejection: Throwable): OperationAdmission = synchronized(monitor) {
        when (state) {
            State.IDLE -> {
                activeRootOperations++
                OperationAdmission.Allowed(RootOperationPermit(this))
            }
            State.STARTING -> OperationAdmission.Rejected(recordPendingFailureLocked(rejection))
            State.ACTIVE -> OperationAdmission.Rejected(
                requireNotNull(activeScope).recordFailure(rejection),
            )
            State.FINALIZING -> OperationAdmission.Rejected(rejection)
        }
    }

    /** Starts a scope fail-fast; a scope never waits while an ordinary root query is running. */
    fun tryStartScope(nestedFailure: Throwable): Throwable? = synchronized(monitor) {
        when (state) {
            State.IDLE -> {
                if (activeRootOperations != 0) {
                    IllegalStateException("Cannot start a managed scope while a root query is in progress")
                } else {
                    state = State.STARTING
                    pendingFailure = null
                    pendingFatal = null
                    null
                }
            }
            State.STARTING -> {
                recordPendingFailureLocked(nestedFailure)
                nestedFailure
            }
            State.ACTIVE -> {
                requireNotNull(activeScope).recordFailure(nestedFailure)
                nestedFailure
            }
            State.FINALIZING -> nestedFailure
        }
    }

    /** Publishes the acquired Scope and transfers any rejection recorded during Starting. */
    fun activate(scope: TransactionExecutionScope) = synchronized(monitor) {
        check(state == State.STARTING) { "Managed scope coordinator is not Starting" }
        activeScope = scope
        state = State.ACTIVE
        pendingFailure?.let(scope::recordFailure)
        pendingFatal?.let(scope::recordFatal)
        pendingFailure = null
        pendingFatal = null
    }

    /** Atomically seals the Scope state before commit, rollback, or close finalization begins. */
    fun seal(scope: TransactionExecutionScope) = synchronized(monitor) {
        check(state == State.ACTIVE && activeScope === scope) { "Managed scope coordinator is not Active" }
        scope.end()
        state = State.FINALIZING
    }

    /** Routes callback exceptions raised while delivering a rejected root operation. */
    fun recordRejectedCallbackThrowable(failure: Throwable) = synchronized(monitor) {
        when (state) {
            State.STARTING -> {
                recordPendingFailureLocked(failure)
                if (failure is Error && pendingFatal == null) pendingFatal = failure
            }
            State.ACTIVE -> {
                val scope = requireNotNull(activeScope)
                scope.recordFailure(failure)
                if (failure is Error) scope.recordFatal(failure)
            }
            State.IDLE, State.FINALIZING -> Unit
        }
    }

    /** Returns to Idle only after all cleanup for the managed Scope has been attempted. */
    fun finishScope() = synchronized(monitor) {
        state = State.IDLE
        activeScope = null
        pendingFailure = null
        pendingFatal = null
    }

    private fun recordPendingFailureLocked(failure: Throwable): Throwable {
        if (pendingFailure == null) pendingFailure = failure
        if (failure is Error && pendingFatal == null) pendingFatal = failure
        return pendingFailure ?: failure
    }

    private fun releaseRootOperation() = synchronized(monitor) {
        check(activeRootOperations > 0) { "Root operation permit released without an active operation" }
        activeRootOperations--
    }

    internal class RootOperationPermit internal constructor(
        private val coordinator: RootExecutionCoordinator,
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) coordinator.releaseRootOperation()
        }
    }
}
