package gog.my_project.data_base_operation.then

import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext

/**
 * Guarded Operation capabilities scoped to one typed Pipeline stage. The wrapper deliberately
 * exposes no Pipeline transition method; the next stage receives the returned value directly.
 */
class TypedOperationPipelineContext internal constructor(
    private val delegate: OperationPipelineStageContext<Unit>,
) : OperationExecutionContext by delegate {
    internal fun finishStage() {
        delegate.finishStage()
    }

    internal fun expire() {
        delegate.expire()
    }
}
