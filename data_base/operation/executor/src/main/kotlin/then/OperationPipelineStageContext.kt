package gog.my_project.data_base_operation.then

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.DdlExecutionOutcome
import gog.my_project.data_base_operation.executor.transaction.DdlFailurePolicy
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_operation.executor.session.result.SessionConsumer
import gog.my_project.data_base_session.api.interfaces.api.ISessionCommandApi
import gog.my_project.data_base_session.api.result.SessionExecutionResult
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.acquire.IAcquireSessionAdvisoryLockApi
import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.release.IReleaseSessionAdvisoryLockApi
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import java.util.concurrent.atomic.AtomicReference

/**
 * Stage-scoped view of an Operation context. It retains the Operation's guarded execution API,
 * but never exposes its JDBC Connection. Each instance is valid only while its Stage is running.
 */
class OperationPipelineStageContext<T> internal constructor(
    private val delegate: OperationExecutionContext,
    private val ownerThread: Thread,
    private val hasNextStage: Boolean,
) : OperationExecutionContext {
    private val protocolFailure = AtomicReference<IllegalStateException?>(null)
    @Volatile
    private var active = true
    private var nextAttempted = false
    private var requestedValue: RequestedValue<T>? = null

    /**
     * Requests a transition to the next configured Stage. The transition is control flow and is
     * independent of the Operation failure latch. Phase 1 supports only [ConnectionDecision.REUSE].
     */
    fun next(value: T, connection: ConnectionDecision = ConnectionDecision.REUSE): T {
        beginNextAttempt()
        completeNext(value, connection)
        return value
    }

    /** Reserves this stage's single continuation attempt for the typed continuation facade. */
    internal fun beginNextAttempt() {
        checkStageAccess()
        if (nextAttempted) {
            failProtocol("next() may be called only once for each Pipeline Stage")
        }
        nextAttempted = true
        if (!hasNextStage) {
            failProtocol("next() cannot be called from the final Pipeline Stage")
        }
    }

    /** Completes a reserved continuation without changing the Operation lifecycle. */
    internal fun completeNext(value: T, connection: ConnectionDecision) {
        checkStageAccess()
        if (requestedValue != null) {
            failProtocol("A Pipeline continuation can be resolved only once")
        }
        if (connection != ConnectionDecision.REUSE) {
            failProtocol("ConnectionDecision.NEW is not supported in this Pipeline")
        }
        requestedValue = RequestedValue(value)
    }

    internal fun protocolViolation(message: String): Nothing = failProtocol(message)

    override val databasePlatformConfig
        get() = withStageAccess { delegate.databasePlatformConfig }

    override fun executeDdl(
        sql: String,
        parameters: List<OperationParameter>,
    ): ExecuteResult<Boolean> = withStageAccess { delegate.executeDdl(sql, parameters) }

    override fun <A : ISessionCommandAst, R : SessionExecutionResult> session(
        api: ISessionCommandApi<A, R>,
    ): SessionConsumer<R> = withStageAccess {
        val delegateConsumer = delegate.session(api)
        SessionConsumer {
            withStageAccess { delegateConsumer.execute() }
        }
    }

    override fun acquireSessionAdvisoryLock(
        api: IAcquireSessionAdvisoryLockApi,
    ): ExecuteResult<AcquireSessionLockResult> =
        withStageAccess { delegate.acquireSessionAdvisoryLock(api) }

    override fun acquireSessionAdvisoryLock(
        ast: IAcquireSessionAdvisoryLockAst,
    ): ExecuteResult<AcquireSessionLockResult> =
        withStageAccess { delegate.acquireSessionAdvisoryLock(ast) }

    override fun releaseSessionAdvisoryLock(
        api: IReleaseSessionAdvisoryLockApi,
    ): ExecuteResult<ReleaseSessionLockResult> =
        withStageAccess { delegate.releaseSessionAdvisoryLock(api) }

    override fun releaseSessionAdvisoryLock(
        ast: IReleaseSessionAdvisoryLockAst,
    ): ExecuteResult<ReleaseSessionLockResult> =
        withStageAccess { delegate.releaseSessionAdvisoryLock(ast) }

    override fun executeDdlWithPolicy(
        sql: String,
        parameters: List<OperationParameter>,
        failurePolicy: DdlFailurePolicy,
    ): DdlExecutionOutcome = withStageAccess {
        delegate.executeDdlWithPolicy(sql, parameters, failurePolicy)
    }

    override fun <R> executeRead(
        sql: String,
        parameters: List<OperationParameter>,
        read: (OperationQueryRows) -> R,
    ): ExecuteResult<R> = withStageAccess { delegate.executeRead(sql, parameters, read) }

    override fun <R> executeSelect(
        query: BuiltQuery,
        read: (OperationQueryRows) -> R,
    ): ExecuteResult<R> = withStageAccess { delegate.executeSelect(query, read) }

    override fun <R> executeSelect(
        query: BoundQuery,
        read: (OperationQueryRows) -> R,
    ): ExecuteResult<R> = withStageAccess { delegate.executeSelect(query, read) }

    override fun executeInsert(query: BuiltQuery): ExecuteResult<Long> =
        withStageAccess { delegate.executeInsert(query) }

    override fun executeInsert(query: BoundQuery): ExecuteResult<Long> =
        withStageAccess { delegate.executeInsert(query) }

    override fun executeUpdate(query: BuiltQuery): ExecuteResult<Int> =
        withStageAccess { delegate.executeUpdate(query) }

    override fun executeUpdate(query: BoundQuery): ExecuteResult<Int> =
        withStageAccess { delegate.executeUpdate(query) }

    override fun executeDelete(query: BuiltQuery): ExecuteResult<Int> =
        withStageAccess { delegate.executeDelete(query) }

    override fun executeDelete(query: BoundQuery): ExecuteResult<Int> =
        withStageAccess { delegate.executeDelete(query) }

    override fun <R> failed(failure: Throwable): ExecuteResult<R> =
        withStageAccess { delegate.failed(failure) }

    internal fun finishStage(): StageResult<T> {
        protocolFailure.get()?.let { throw it }
        if (nextAttempted && requestedValue == null) {
            failProtocol("next() must be completed with its lifecycle decision before the Stage returns")
        }
        val continuation = requestedValue ?: return StageResult.Complete
        return StageResult.Continue(continuation.value)
    }

    internal fun expire() {
        active = false
    }

    private fun checkStageAccess() {
        if (!active) {
            throw IllegalStateException("Pipeline Stage context is no longer active")
        }
        if (Thread.currentThread() !== ownerThread) {
            val failure = IllegalStateException("Pipeline Stage context may only be used by its owner thread")
            protocolFailure.compareAndSet(null, failure)
            throw failure
        }
    }

    private inline fun <R> withStageAccess(block: () -> R): R {
        checkStageAccess()
        return block()
    }

    private fun failProtocol(message: String): Nothing {
        val failure = IllegalStateException(message)
        protocolFailure.compareAndSet(null, failure)
        throw failure
    }

    private data class RequestedValue<T>(val value: T)

    internal sealed interface StageResult<out T> {
        data object Complete : StageResult<Nothing>
        data class Continue<T>(val value: T) : StageResult<T>
    }
}
