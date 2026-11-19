package gog.my_project.data_base_operation.then

import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Guarded Operation capabilities and shared State for one Stage invocation.
 *
 * This context deliberately exposes neither the Scope nor its JDBC Connection. It expires when its
 * Stage returns or throws, and its State property is restricted to the Stage's owner thread.
 */
class OperationPipelineStageExecutionContext<State : Any> internal constructor(
    private val frame: OperationPipelineExecutionFrame<State>,
    private val operationContext: OperationPipelineStageContext<Unit>,
) : OperationExecutionContext by operationContext {
    private val ownerThread = Thread.currentThread()

    @Volatile
    private var active = true

    private val stateAccessFailure = AtomicReference<IllegalStateException?>(null)

    /** Shared execution-local State. */
    val state: State
        get() {
            checkStateAccess()
            return frame.state
        }

    /** Checks remembered protocol violations after a callback that may have caught one. */
    internal fun finishStage() {
        stateAccessFailure.get()?.let { throw it }
        operationContext.finishStage()
    }

    internal fun expire() {
        active = false
        operationContext.expire()
    }

    private fun checkStateAccess() {
        if (!active) {
            throw IllegalStateException("Pipeline Stage context is no longer active")
        }
        if (Thread.currentThread() !== ownerThread) {
            val failure = IllegalStateException("Pipeline Stage context may only be used by its owner thread")
            stateAccessFailure.compareAndSet(null, failure)
            throw stateAccessFailure.get() ?: failure
        }
        stateAccessFailure.get()?.let { throw it }
    }
}
