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
    private var preparedScope: OperationExecutionScope? = null
    private var lifecycleThread: Thread? = null
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
                    lifecycleThread = Thread.currentThread()
                    preparedScope = null
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

    /** Registers the scope internally before it is published to a callback. */
    internal fun prepareScope(scope: OperationExecutionScope) = synchronized(monitor) {
        check(state == State.STARTING && lifecycleThread === Thread.currentThread()) {
            "Managed scope can only be prepared by its starting Operation thread"
        }
        check(preparedScope == null) { "Managed scope is already prepared" }
        preparedScope = scope
    }

    /**
     * Compatibility entry point retained for cross-module source compatibility. Scope transitions
     * are owned by Operation; attempted external transitions become a scope failure.
     */
    @Deprecated("Scope activation is owned by Operation executors")
    fun activate(scope: OperationExecutionScope): Unit = synchronized(monitor) {
        val misuse = IllegalStateException("Managed scope activation is owned by Operation executors")
        when (state) {
            State.STARTING -> recordPendingFailureLocked(misuse)
            State.ACTIVE -> activeScope?.recordFailure(misuse)
            State.IDLE, State.FINALIZING -> Unit
        }
        throw misuse
    }

    /** Publishes the acquired scope and transfers rejections observed during Starting. */
    internal fun activateOwned(scope: OperationExecutionScope) = synchronized(monitor) {
        check(state == State.STARTING) { "Managed scope coordinator is not Starting" }
        check(lifecycleThread === Thread.currentThread()) { "Managed scope activation must run on its Operation thread" }
        check(preparedScope === scope) { "Only the prepared Operation scope can be activated" }
        activeScope = scope
        state = State.ACTIVE
        pendingFailure?.let(scope::recordFailure)
        pendingFatal?.let(scope::recordFatal)
        pendingFailure = null
        pendingFatal = null
    }

    /** Compatibility method that prevents callers from sealing a live scope. */
    @Deprecated("Scope sealing is owned by Operation executors")
    fun seal(scope: OperationExecutionScope): Unit = synchronized(monitor) {
        val misuse = IllegalStateException("Managed scope sealing is owned by Operation executors")
        if (state == State.ACTIVE) activeScope?.recordFailure(misuse)
        throw misuse
    }

    /** Seals the callback result atomically before commit, rollback, or close begins. */
    internal fun sealOwned(scope: OperationExecutionScope) = synchronized(monitor) {
        check(state == State.ACTIVE && activeScope === scope) { "Managed scope coordinator is not Active" }
        check(lifecycleThread === Thread.currentThread()) { "Managed scope must be sealed by its Operation thread" }
        scope.endForLifecycle()
        state = State.FINALIZING
    }

    /** Best-effort terminal transition used after a lifecycle failure so cleanup still runs. */
    internal fun abortScope(scope: OperationExecutionScope, failure: Throwable) = synchronized(monitor) {
        if (activeScope === scope && (state == State.ACTIVE || state == State.FINALIZING)) {
            scope.recordFailure(failure)
            if (failure is Error) scope.recordFatal(failure)
            scope.endForLifecycle()
            state = State.FINALIZING
        } else if (state == State.STARTING && preparedScope === scope) {
            scope.recordFailure(failure)
            if (failure is Error) scope.recordFatal(failure)
        }
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

    /** Compatibility method; cleanup ownership remains with the Operation executor. */
    @Deprecated("Scope cleanup is owned by Operation executors")
    fun finishScope() = synchronized(monitor) {
        when (state) {
            State.IDLE -> Unit
            State.STARTING -> {
                val misuse = IllegalStateException("A Starting scope cannot be finished by a consumer")
                recordPendingFailureLocked(misuse)
                throw misuse
            }
            State.ACTIVE -> {
                val misuse = IllegalStateException("An Active scope cannot be finished by a consumer")
                activeScope?.recordFailure(misuse)
                throw misuse
            }
            State.FINALIZING -> throw IllegalStateException("Scope cleanup is still owned by Operation")
        }
    }

    /** Returns to Idle only after Operation has attempted all owned cleanup. */
    internal fun finishOwnedScope() = synchronized(monitor) {
        check(lifecycleThread === Thread.currentThread()) { "Scope cleanup must run on its Operation thread" }
        if (state == State.ACTIVE) {
            val unfinished = activeScope
            if (unfinished != null) {
                val failure = IllegalStateException("Operation scope ended before callback sealing")
                unfinished.recordFailure(failure)
                unfinished.endForLifecycle()
            }
        }
        activeScope?.endForLifecycle()
        state = State.IDLE
        activeScope = null
        preparedScope = null
        lifecycleThread = null
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
