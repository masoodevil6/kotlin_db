package gog.my_project.data_base_operation.executor.transaction

import java.util.concurrent.atomic.AtomicBoolean

/** Coordinates root operations and managed scopes for one executor lineage. */
class OperationExecutionCoordinator(
    private val rootOperationDescription: String = "root operation",
) {
    private enum class State { IDLE, STARTING, ACTIVE, FINALIZING }

    private val monitor = Any()
    private var state = State.IDLE
    private var activeRootOperations = 0
    private var activeScope: OperationExecutionScope? = null
    private var pendingFailure: Throwable? = null
    private var pendingFatal: Error? = null

    sealed interface OperationAdmission {
        class Allowed(val permit: RootOperationPermit) : OperationAdmission
        class Rejected(val failure: Throwable) : OperationAdmission
    }

    /** Admits a synchronous root operation only when no managed scope is starting or active. */
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

    /** Starts a scope fail-fast; a scope never waits while an ordinary root operation is running. */
    fun tryStartScope(nestedFailure: Throwable): Throwable? = synchronized(monitor) {
        when (state) {
            State.IDLE -> {
                if (activeRootOperations != 0) {
                    IllegalStateException(
                        "Cannot start a managed scope while a $rootOperationDescription is in progress",
                    )
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

    /** Publishes the acquired scope and transfers rejections observed during Starting. */
    fun activate(scope: OperationExecutionScope) = synchronized(monitor) {
        check(state == State.STARTING) { "Managed scope coordinator is not Starting" }
        activeScope = scope
        state = State.ACTIVE
        pendingFailure?.let(scope::recordFailure)
        pendingFatal?.let(scope::recordFatal)
        pendingFailure = null
        pendingFatal = null
    }

    /** Seals the callback result atomically before commit, rollback, or close begins. */
    fun seal(scope: OperationExecutionScope) = synchronized(monitor) {
        check(state == State.ACTIVE && activeScope === scope) { "Managed scope coordinator is not Active" }
        scope.end()
        state = State.FINALIZING
    }

    /** Routes a throwable from a rejected root callback into a scope that is still accepting failures. */
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

    /** Returns to Idle only after scope cleanup has been attempted. */
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

    class RootOperationPermit internal constructor(
        private val coordinator: OperationExecutionCoordinator,
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) coordinator.releaseRootOperation()
        }
    }
}
