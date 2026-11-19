package gog.my_project.data_base_operation.then

/**
 * A reusable, deferred recipe for one typed Operation Pipeline.
 *
 * The recipe is materialized for each execution after Operation admission. The State Factory is
 * invoked once for that execution, after recipe materialization and before resource acquisition.
 */
class OperationPipelineDefinition<State : Any, Result> private constructor(
    private val stateFactory: () -> State,
    private val recipe: OperationPipelineDefinitionScope<State, Result>.() ->
        OperationPipelineBuilder<State, Result, Result>,
) {

    /** Materializes a fresh immutable Plan and execution-local State Frame. */
    internal fun prepareExecution(): OperationPipelineExecution<State, Result> {
        val definitionScope = OperationPipelineDefinitionScope<State, Result>()
        val builder = definitionScope.recipe()
        val frame = OperationPipelineExecutionFrame(stateFactory())
        return OperationPipelineExecution(builder.plan, frame)
    }

    companion object {
        /** Creates a deferred recipe; neither [stateFactory] nor [recipe] runs here. */
        fun <State : Any, Result> create(
            stateFactory: () -> State,
            recipe: OperationPipelineDefinitionScope<State, Result>.() ->
                OperationPipelineBuilder<State, Result, Result>,
        ): OperationPipelineDefinition<State, Result> =
            OperationPipelineDefinition(stateFactory, recipe)
    }
}

/** DSL scope used only while materializing a Definition recipe. */
class OperationPipelineDefinitionScope<State : Any, Result> internal constructor() {
    /** Declares the required first Stage and its output type. */
    fun <Initial> start(
        stage: (context: OperationPipelineStageExecutionContext<State>) -> Initial,
    ): OperationPipelineBuilder<State, Initial, Result> =
        OperationPipelineBuilder(OperationPipelinePlan.start(stage))

    /**
     * Appends a Stage whose input must exactly match the preceding Builder's current output.
     * Both types are explicit at the call site and remain connected through the invariant Builder.
     */
    fun <In, Out> OperationPipelineBuilder<State, In, Result>.then(
        stage: (input: In, context: OperationPipelineStageExecutionContext<State>) -> Out,
    ): OperationPipelineBuilder<State, Out, Result> =
        OperationPipelineBuilder(plan.then(stage))
}

/** Immutable typed Builder value produced while a Definition recipe is materialized. */
class OperationPipelineBuilder<State : Any, Current, Result> internal constructor(
    internal val plan: OperationPipelinePlan<State, Current>,
)
