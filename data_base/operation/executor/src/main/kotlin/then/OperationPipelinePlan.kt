package gog.my_project.data_base_operation.then

import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.util.concurrent.atomic.AtomicBoolean

/** Internal result of a Plan run. Stopped carries no value of the Plan's output type. */
internal sealed interface StageOutcome<out T> {
    data class Completed<out T>(val value: T) : StageOutcome<T>
    data object Stopped : StageOutcome<Nothing>
}

/** Immutable State container for one admitted Pipeline execution. */
internal class OperationPipelineExecutionFrame<State : Any>(
    val state: State,
)

/** Materialized, execution-local pair consumed by the Runner exactly once. */
internal class OperationPipelineExecution<State : Any, Result>(
    internal val plan: OperationPipelinePlan<State, Result>,
    internal val frame: OperationPipelineExecutionFrame<State>,
) {
    private val claimed = AtomicBoolean(false)

    internal fun claim() {
        check(claimed.compareAndSet(false, true)) {
            "A prepared Operation Pipeline execution can only be run once"
        }
    }
}

/**
 * Structurally immutable typed composition. Each append captures the prior Plan and one Stage;
 * Stopped propagates covariantly and never requires a fabricated [Current] or [Next] value.
 */
internal class OperationPipelinePlan<State : Any, Current> private constructor(
    private val runPlan: (
        frame: OperationPipelineExecutionFrame<State>,
        scope: OperationExecutionScope,
    ) -> StageOutcome<Current>,
) {
    fun <Next> then(
        stage: (input: Current, context: OperationPipelineStageExecutionContext<State>) -> Next,
    ): OperationPipelinePlan<State, Next> = OperationPipelinePlan { frame, scope ->
        when (val previous = runPlan(frame, scope)) {
            StageOutcome.Stopped -> StageOutcome.Stopped
            is StageOutcome.Completed -> {
                if (hasOrdinaryFailureAtBoundary(scope)) {
                    StageOutcome.Stopped
                } else {
                    StageOutcome.Completed(invokeStage(frame, scope, previous.value, stage))
                }
            }
        }
    }

    internal fun run(
        frame: OperationPipelineExecutionFrame<State>,
        scope: OperationExecutionScope,
    ): StageOutcome<Current> = runPlan(frame, scope)

    companion object {
        fun <State : Any, Initial> start(
            stage: (context: OperationPipelineStageExecutionContext<State>) -> Initial,
        ): OperationPipelinePlan<State, Initial> = OperationPipelinePlan { frame, scope ->
            StageOutcome.Completed(invokeStage(frame, scope, input = Unit) { _, context -> stage(context) })
        }
    }
}

/**
 * Runs a materialized Plan after lifecycle admission and resource acquisition have been handled by
 * Operation. The initial Failure Owner check is immediately before first-Stage dispatch.
 */
internal object OperationPipelineRunner {
    fun <State : Any, Result> run(
        execution: OperationPipelineExecution<State, Result>,
        scope: OperationExecutionScope,
    ): StageOutcome<Result> {
        execution.claim()
        if (hasOrdinaryFailureAtBoundary(scope)) return StageOutcome.Stopped
        return execution.plan.run(execution.frame, scope)
    }
}

private fun hasOrdinaryFailureAtBoundary(scope: OperationExecutionScope): Boolean =
    when (val failure = scope.operationFailureOwner.propagationFailure) {
        null -> false
        is Error -> throw failure
        else -> true
    }

private inline fun <State : Any, Input, Output> invokeStage(
    frame: OperationPipelineExecutionFrame<State>,
    scope: OperationExecutionScope,
    input: Input,
    stage: (Input, OperationPipelineStageExecutionContext<State>) -> Output,
): Output {
    val operationContext = OperationPipelineStageContext<Unit>(
        delegate = scope,
        ownerThread = Thread.currentThread(),
        hasNextStage = false,
    )
    val context = OperationPipelineStageExecutionContext(frame, operationContext)
    return try {
        val result = stage(input, context)
        context.finishStage()
        result
    } finally {
        context.expire()
    }
}
