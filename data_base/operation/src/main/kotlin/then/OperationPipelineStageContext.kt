package gog.my_project.data_base_operation.then

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.DdlExecutionOutcome
import gog.my_project.data_base_operation.executor.transaction.DdlFailurePolicy
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
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
    private var requestedValue: Any? = null
    private var hasRequestedValue = false

    /**
     * Requests a transition to the next configured Stage. The transition is control flow and is
     * independent of the Operation failure latch. Phase 1 supports only [ConnectionDecision.REUSE].
     */
    fun next(value: T, connection: ConnectionDecision = ConnectionDecision.REUSE): T {
        checkStageAccess()
        if (nextAttempted) {
            failProtocol("next() may be called only once for each Pipeline Stage")
        }
        nextAttempted = true
        if (!hasNextStage) {
            failProtocol("next() cannot be called from the final Pipeline Stage")
        }
        if (connection != ConnectionDecision.REUSE) {
            failProtocol("ConnectionDecision.NEW is not supported in Pipeline Phase 1")
        }

        requestedValue = value
        hasRequestedValue = true
        return value
    }

    override val databasePlatformConfig
        get() = withStageAccess { delegate.databasePlatformConfig }

    override fun executeDdl(
        sql: String,
        parameters: List<OperationParameter>,
    ): ExecuteResult<Boolean> = withStageAccess { delegate.executeDdl(sql, parameters) }

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

    internal fun finishStage(): StageResult {
        protocolFailure.get()?.let { throw it }
        return if (hasRequestedValue) StageResult.Continue(requestedValue) else StageResult.Complete
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

    internal sealed interface StageResult {
        data object Complete : StageResult
        data class Continue(val value: Any?) : StageResult
    }
}
