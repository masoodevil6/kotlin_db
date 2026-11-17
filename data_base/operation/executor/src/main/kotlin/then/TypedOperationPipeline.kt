package gog.my_project.data_base_operation.then

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope

/**
 * An immutable, synchronously executed pipeline whose input type [I] and current output type [O]
 * are tracked independently. Each stage receives a fresh, stage-scoped context over the same
 * Operation context.
 */
class TypedOperationPipeline<I, O> private constructor(
    private val run: (input: I, operationContext: OperationExecutionContext) -> O,
) {
    /** Appends a stage that consumes the current output and produces [N]. */
    fun <N> then(
        stage: (result: O, context: TypedOperationPipelineContext) -> N,
    ): TypedOperationPipeline<I, N> {
        val previous = run
        return TypedOperationPipeline { input, operationContext ->
            val previousResult = previous(input, operationContext)
            runTypedStage(previousResult, operationContext, stage)
        }
    }

    internal fun executeIn(
        input: I,
        operationScope: OperationExecutionScope,
    ): ExecuteResult<O> {
        val result = run(input, operationScope)
        operationScope.fatalError?.let { throw it }
        return operationScope.firstFailure
            ?.let { ExecuteResult.Failure(it) }
            ?: ExecuteResult.Success(result)
    }

    companion object {
        /** Creates a pipeline with its first typed stage. */
        fun <I, O> start(
            stage: (input: I, context: TypedOperationPipelineContext) -> O,
        ): TypedOperationPipeline<I, O> = TypedOperationPipeline { input, operationContext ->
            runTypedStage(input, operationContext, stage)
        }
    }
}

/** Executes all stages under one Operation-owned Connection scope. */
fun <I, O> OperationExecutionExecutor.executePipeline(
    pipeline: TypedOperationPipeline<I, O>,
    input: I,
): ExecuteResult<O> = connection { scope -> pipeline.executeIn(input, scope) }

/** Executes all stages against this existing scope without changing its lifecycle. */
fun <I, O> OperationExecutionScope.executePipeline(
    pipeline: TypedOperationPipeline<I, O>,
    input: I,
): ExecuteResult<O> = try {
    pipeline.executeIn(input, this)
} catch (error: Error) {
    recordFatal(error)
    throw error
} catch (failure: Throwable) {
    recordFailure(failure)
    throw failure
}

private fun <I, O> runTypedStage(
    input: I,
    operationContext: OperationExecutionContext,
    stage: (input: I, context: TypedOperationPipelineContext) -> O,
): O {
    val guardedContext = OperationPipelineStageContext<Unit>(
        delegate = operationContext,
        ownerThread = Thread.currentThread(),
        hasNextStage = false,
    )
    val stageContext = TypedOperationPipelineContext(guardedContext)
    return try {
        val result = stage(input, stageContext)
        stageContext.finishStage()
        result
    } finally {
        stageContext.expire()
    }
}
