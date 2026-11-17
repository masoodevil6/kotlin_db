package gog.my_project.data_base_operation.then

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope

/**
 * The terminal value of a multi-stage Pipeline. Earlier stages may finish normally, so a chain
 * with heterogeneous stage outputs represents those possible terminal values as a nested sum.
 */
sealed interface PipelineCompletion<out Earlier, out Current> {
    data class Earlier<out T>(val value: T) : PipelineCompletion<T, Nothing>
    data class Current<out T>(val value: T) : PipelineCompletion<Nothing, T>
}

/** Connection-lifecycle Pipeline. It can only be run through an Operation Connection Scope. */
class ConnectionTypedOperationPipeline<I, O> internal constructor(
    private val program: PipelineProgram<I, O>,
) {
    /** Appends a stage whose result type [N] becomes the next stage's input type. */
    fun <N> then(
        stage: (result: I, context: ConnectionPipelineStageContext<N>) -> N,
    ): ConnectionTypedOperationPipeline<N, PipelineCompletion<O, N>> =
        ConnectionTypedOperationPipeline(
            program.then { input, scope, hasNext ->
                runConnectionStage(scope, hasNext) { context -> stage(input, context) }
            },
        )

    /** Runs this complete program once in a newly owned Connection Scope. */
    fun execute(executor: OperationExecutionExecutor): ExecuteResult<O> =
        executor.connection { scope -> program.execute(scope) }

    companion object {
        /** Builds the first stage; its output is also the first possible terminal value. */
        fun <O> start(
            stage: (context: ConnectionPipelineStageContext<O>) -> O,
        ): ConnectionTypedOperationPipeline<O, O> = ConnectionTypedOperationPipeline(
            PipelineProgram.start { scope, hasNext -> runConnectionStage(scope, hasNext, stage) },
        )
    }
}

/** Transaction-lifecycle Pipeline. Its stage context deliberately has no connection decision API. */
class TransactionTypedOperationPipeline<I, O> internal constructor(
    private val program: PipelineProgram<I, O>,
) {
    /** Appends a stage whose result type [N] becomes the next stage's input type. */
    fun <N> then(
        stage: (result: I, context: TransactionPipelineStageContext<N>) -> N,
    ): TransactionTypedOperationPipeline<N, PipelineCompletion<O, N>> =
        TransactionTypedOperationPipeline(
            program.then { input, scope, hasNext ->
                runTransactionStage(scope, hasNext) { context -> stage(input, context) }
            },
        )

    /** Runs this complete program once in a newly owned Transaction Scope. */
    fun execute(executor: OperationExecutionExecutor): ExecuteResult<O> =
        executor.transaction { scope -> program.execute(scope) }

    companion object {
        /** Builds the first stage; its output is also the first possible terminal value. */
        fun <O> start(
            stage: (context: TransactionPipelineStageContext<O>) -> O,
        ): TransactionTypedOperationPipeline<O, O> = TransactionTypedOperationPipeline(
            PipelineProgram.start { scope, hasNext -> runTransactionStage(scope, hasNext, stage) },
        )
    }
}

/** One shared continuation engine for both lifecycle-specific facades. */
internal class PipelineProgram<I, O> private constructor(
    private val run: (scope: OperationExecutionScope, hasNextStage: Boolean) -> PipelineRun<I, O>,
) {
        fun <N> then(
        stage: (input: I, scope: OperationExecutionScope, hasNextStage: Boolean) -> PipelineRun<N, N>,
    ): PipelineProgram<N, PipelineCompletion<O, N>> = PipelineProgram { scope, hasNextStage ->
        when (val previous = run(scope, true)) {
            is PipelineRun.Complete ->
                PipelineRun.Complete(PipelineCompletion.Earlier(previous.value))

            is PipelineRun.Continue -> when (val current = stage(previous.value, scope, hasNextStage)) {
                is PipelineRun.Continue -> PipelineRun.Continue(current.value)
                is PipelineRun.Complete ->
                    PipelineRun.Complete(PipelineCompletion.Current(current.value))
            }
        }
    }

    fun execute(scope: OperationExecutionScope): ExecuteResult<O> = when (val outcome = run(scope, false)) {
        is PipelineRun.Continue -> throw IllegalStateException(
            "The final Pipeline Stage requested a continuation",
        )

        is PipelineRun.Complete -> {
            scope.fatalError?.let { throw it }
            scope.firstFailure?.let { ExecuteResult.Failure(it) } ?: ExecuteResult.Success(outcome.value)
        }
    }

    companion object {
        fun <O> start(
            stage: (scope: OperationExecutionScope, hasNextStage: Boolean) -> PipelineRun<O, O>,
        ): PipelineProgram<O, O> = PipelineProgram { scope, hasNextStage ->
            stage(scope, hasNextStage)
        }
    }
}

internal sealed interface PipelineRun<out I, out O> {
    data class Continue<I>(val value: I) : PipelineRun<I, Nothing>
    data class Complete<O>(val value: O) : PipelineRun<Nothing, O>
}

private fun <O> runConnectionStage(
    scope: OperationExecutionScope,
    hasNextStage: Boolean,
    stage: (ConnectionPipelineStageContext<O>) -> O,
): PipelineRun<O, O> {
    val context = ConnectionPipelineStageContext(
        OperationPipelineStageContext<O>(scope, Thread.currentThread(), hasNextStage),
    )
    return try {
        val result = stage(context)
        when (val transition = context.finishStage()) {
            OperationPipelineStageContext.StageResult.Complete -> PipelineRun.Complete(result)
            is OperationPipelineStageContext.StageResult.Continue -> PipelineRun.Continue(transition.value)
        }
    } finally {
        context.expire()
    }
}

private fun <O> runTransactionStage(
    scope: OperationExecutionScope,
    hasNextStage: Boolean,
    stage: (TransactionPipelineStageContext<O>) -> O,
): PipelineRun<O, O> {
    val context = TransactionPipelineStageContext(
        OperationPipelineStageContext<O>(scope, Thread.currentThread(), hasNextStage),
    )
    return try {
        val result = stage(context)
        when (val transition = context.finishStage()) {
            OperationPipelineStageContext.StageResult.Complete -> PipelineRun.Complete(result)
            is OperationPipelineStageContext.StageResult.Continue -> PipelineRun.Continue(transition.value)
        }
    } finally {
        context.expire()
    }
}
