package gog.my_project.data_base_operation.then

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext

/** Runs a prebuilt Stage chain serially against one existing Operation context. */
internal class OperationPipelineStageExecutor(
    private val stages: List<OperationPipelineStage>,
) {
    fun <T> execute(context: OperationExecutionContext): ExecuteResult<T> {
        var input: Any? = Unit

        stages.forEachIndexed { index, stage ->
            val stageContext = OperationPipelineStageContext<Any?>(
                delegate = context,
                ownerThread = Thread.currentThread(),
                hasNextStage = index < stages.lastIndex,
            )
            val returnedValue = try {
                stage.execute(input, stageContext)
            } finally {
                stageContext.expire()
            }

            when (val transition = stageContext.finishStage()) {
                OperationPipelineStageContext.StageResult.Complete -> {
                    @Suppress("UNCHECKED_CAST")
                    return ExecuteResult.Success(returnedValue as T)
                }
                is OperationPipelineStageContext.StageResult.Continue<*> -> input = transition.value
            }
        }

        return ExecuteResult.Failure(IllegalStateException("OperationPipeline has no configured Stages"))
    }
}

internal fun interface OperationPipelineStage {
    fun execute(input: Any?, context: OperationPipelineStageContext<Any?>): Any?
}
