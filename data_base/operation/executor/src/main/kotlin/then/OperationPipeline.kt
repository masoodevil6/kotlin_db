package gog.my_project.data_base_operation.then

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope

/**
 * Immutable, synchronously executed chain of Operation Stages. Each Stage uses the Pipeline's
 * payload type. A Stage must call its Context's [OperationPipelineStageContext.next] to continue;
 * otherwise its returned value completes the Pipeline.
 */
class OperationPipeline<T> private constructor(
    private val stages: List<OperationPipelineStage>,
) {
    /** Appends a Stage that accepts the preceding Stage's value and produces its own value. */
    fun then(
        stage: OperationPipelineStageContext<T>.(T) -> T,
    ): OperationPipeline<T> {
        val appended = OperationPipelineStage { input, context ->
            @Suppress("UNCHECKED_CAST")
            stage(context as OperationPipelineStageContext<T>, input as T)
        }
        return OperationPipeline(stages + appended)
    }

    internal fun executeIn(context: OperationExecutionContext): ExecuteResult<T> =
        OperationPipelineStageExecutor(stages).execute(context)

    companion object {
        /** Creates a Pipeline with its first Stage. The first Stage has no incoming payload. */
        fun <T> start(
            stage: OperationPipelineStageContext<T>.() -> T,
        ): OperationPipeline<T> = OperationPipeline(
            listOf(
                OperationPipelineStage { _, context ->
                    @Suppress("UNCHECKED_CAST")
                    stage(context as OperationPipelineStageContext<T>)
                },
            ),
        )
    }
}

/** Runs the whole Pipeline as one Operation Connection Scope. */
fun <T> OperationExecutionExecutor.executePipeline(
    pipeline: OperationPipeline<T>,
): ExecuteResult<T> = connection { scope -> pipeline.executeIn(scope) }

/** Runs the Pipeline synchronously inside this already-owned Scope, including an active lock scope. */
fun <T> OperationExecutionScope.executePipeline(
    pipeline: OperationPipeline<T>,
): ExecuteResult<T> = pipeline.executeIn(this)
