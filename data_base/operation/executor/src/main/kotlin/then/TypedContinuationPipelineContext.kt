package gog.my_project.data_base_operation.then

import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext

/** Guarded capabilities for one Connection Pipeline Stage. */
class ConnectionPipelineStageContext<T> internal constructor(
    private val delegate: OperationPipelineStageContext<T>,
) : OperationExecutionContext by delegate {
    /** Starts this stage's single continuation request. The Connection choice is made explicitly. */
    fun next(value: T): PipelineNext<T> {
        delegate.beginNextAttempt()
        return PipelineNext(
            resolve = { decision ->
                delegate.completeNext(value, decision)
                value
            },
            protocolViolation = delegate::protocolViolation,
        )
    }

    internal fun finishStage(): OperationPipelineStageContext.StageResult<T> = delegate.finishStage()

    internal fun expire() = delegate.expire()
}

/** Guarded capabilities for one Transaction Pipeline Stage; no Connection decision is exposed. */
class TransactionPipelineStageContext<T> internal constructor(
    private val delegate: OperationPipelineStageContext<T>,
) : OperationExecutionContext by delegate {
    /** Starts and records this stage's single continuation request. */
    fun next(value: T): T {
        delegate.beginNextAttempt()
        delegate.completeNext(value, ConnectionDecision.REUSE)
        return value
    }

    internal fun finishStage(): OperationPipelineStageContext.StageResult<T> = delegate.finishStage()

    internal fun expire() = delegate.expire()
}

/** Connection-only continuation token. It is valid only during the Stage that created it. */
class PipelineNext<T> internal constructor(
    private val resolve: (ConnectionDecision) -> T,
    private val protocolViolation: (String) -> Nothing,
) {
    private var decisionAttempted = false

    /** Resolves the continuation on the current Connection; NEW is rejected before any new open. */
    fun connection(decision: ConnectionDecision): T {
        if (decisionAttempted) {
            protocolViolation("A Pipeline continuation can receive only one Connection decision")
        }
        decisionAttempted = true
        return resolve(decision)
    }
}
