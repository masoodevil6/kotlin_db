package gog.my_project.data_base_query.executor.manager

import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionCoordinator

/** Source-compatible internal name for the shared Operation scope state. */
internal typealias TransactionExecutionScope = OperationExecutionScope

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

/** Query adapter for the shared Operation coordinator; it owns no coordination state. */
internal class RootExecutionCoordinator {
    private val operationCoordinator = OperationExecutionCoordinator("root query")

    sealed interface OperationAdmission {
        class Allowed internal constructor(internal val permit: RootOperationPermit) : OperationAdmission
        class Rejected internal constructor(internal val failure: Throwable) : OperationAdmission
    }

    fun acquireRootOperation(rejection: Throwable): OperationAdmission =
        when (val admission = operationCoordinator.acquireRootOperation(rejection)) {
            is OperationExecutionCoordinator.OperationAdmission.Allowed ->
                OperationAdmission.Allowed(RootOperationPermit(admission.permit))
            is OperationExecutionCoordinator.OperationAdmission.Rejected ->
                OperationAdmission.Rejected(admission.failure)
        }

    fun operationCoordinator(): OperationExecutionCoordinator = operationCoordinator

    fun tryStartScope(nestedFailure: Throwable): Throwable? = operationCoordinator.tryStartScope(nestedFailure)

    fun activate(scope: TransactionExecutionScope) = operationCoordinator.activate(scope)

    fun seal(scope: TransactionExecutionScope) = operationCoordinator.seal(scope)

    fun recordRejectedCallbackThrowable(failure: Throwable) =
        operationCoordinator.recordRejectedCallbackThrowable(failure)

    fun finishScope() = operationCoordinator.finishScope()

    internal class RootOperationPermit(
        private val permit: OperationExecutionCoordinator.RootOperationPermit,
    ) {
        fun release() = permit.release()
    }
}
