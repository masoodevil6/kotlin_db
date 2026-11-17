package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.then.ConnectionPipelineStageContext
import gog.my_project.data_base_operation.then.ConnectionTypedOperationPipeline
import gog.my_project.data_base_operation.then.PipelineCompletion
import gog.my_project.data_base_operation.then.TransactionPipelineStageContext
import gog.my_project.data_base_operation.then.TransactionTypedOperationPipeline

/** Manager-bound Connection Pipeline builder. Execution remains lazy until [execute]. */
class DatabaseHandleConnectionPipeline<I, O> internal constructor(
    private val handle: DatabaseHandleApi,
    private val pipeline: ConnectionTypedOperationPipeline<I, O>,
) {
    fun <N> then(
        stage: (result: I, context: ConnectionPipelineStageContext<N>) -> N,
    ): DatabaseHandleConnectionPipeline<N, PipelineCompletion<O, N>> =
        DatabaseHandleConnectionPipeline(handle, pipeline.then(stage))

    /** Runs the complete chain under one Manager admission lease and one Operation Connection Scope. */
    fun execute(): ExecuteResult<O> {
        val managedHandle = handle as? DatabaseHandle
            ?: return ExecuteResult.Failure(
                UnsupportedOperationException(
                    "This DatabaseHandleApi implementation does not expose managed Pipeline execution",
                ),
            )
        return managedHandle.runConnectionPipeline(pipeline)
    }
}

/** Manager-bound Transaction Pipeline builder. Execution remains lazy until [execute]. */
class DatabaseHandleTransactionPipeline<I, O> internal constructor(
    private val handle: DatabaseHandleApi,
    private val pipeline: TransactionTypedOperationPipeline<I, O>,
) {
    fun <N> then(
        stage: (result: I, context: TransactionPipelineStageContext<N>) -> N,
    ): DatabaseHandleTransactionPipeline<N, PipelineCompletion<O, N>> =
        DatabaseHandleTransactionPipeline(handle, pipeline.then(stage))

    /** Runs the complete chain under one Manager admission lease and one Operation Transaction Scope. */
    fun execute(): ExecuteResult<O> {
        val managedHandle = handle as? DatabaseHandle
            ?: return ExecuteResult.Failure(
                UnsupportedOperationException(
                    "This DatabaseHandleApi implementation does not expose managed Pipeline execution",
                ),
            )
        return managedHandle.runTransactionPipeline(pipeline)
    }
}

/** Begins building a Connection Pipeline for this registration. No Connection opens until execute. */
fun <O> DatabaseHandleApi.connectionPipeline(
    stage: (context: ConnectionPipelineStageContext<O>) -> O,
): DatabaseHandleConnectionPipeline<O, O> = DatabaseHandleConnectionPipeline(
    this,
    ConnectionTypedOperationPipeline.start(stage),
)

/** Begins building a Transaction Pipeline for this registration. No Connection opens until execute. */
fun <O> DatabaseHandleApi.transactionPipeline(
    stage: (context: TransactionPipelineStageContext<O>) -> O,
): DatabaseHandleTransactionPipeline<O, O> = DatabaseHandleTransactionPipeline(
    this,
    TransactionTypedOperationPipeline.start(stage),
)
