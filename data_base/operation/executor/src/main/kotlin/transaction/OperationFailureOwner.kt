package gog.my_project.data_base_operation.executor.transaction

/**
 * The canonical failure state for one managed Operation execution. A Pipeline may own this state
 * before a Scope exists; after activation, the Scope delegates its failure API to the same object.
 */
internal class OperationFailureOwner {
    private val monitor = Any()
    private var primaryFailure: Throwable? = null
    private var fatalFailure: Error? = null
    private val laterFailures = mutableListOf<Throwable>()

    internal val firstFailure: Throwable?
        get() = synchronized(monitor) { primaryFailure }

    internal val fatalError: Error?
        get() = synchronized(monitor) { fatalFailure }

    /** Fatal errors retain Operation's existing propagation precedence over ordinary failures. */
    internal val propagationFailure: Throwable?
        get() = synchronized(monitor) { fatalFailure ?: primaryFailure }

    internal fun recordFailure(failure: Throwable): Throwable = synchronized(monitor) {
        recordFailureLocked(failure)
    }

    internal fun recordFatal(error: Error) {
        synchronized(monitor) {
            recordFatalLocked(error)
        }
    }

    internal fun recordThrowable(failure: Throwable) {
        if (failure is Error) recordFatal(failure) else recordFailure(failure)
    }

    internal fun hasRecordedFailure(failure: Throwable): Boolean = synchronized(monitor) {
        primaryFailure === failure || laterFailures.any { it === failure } || fatalFailure === failure
    }

    private fun recordFailureLocked(failure: Throwable): Throwable {
        val currentPrimary = primaryFailure
        if (currentPrimary == null) {
            primaryFailure = failure
        } else if (currentPrimary !== failure && failure !is Error) {
            if (fatalFailure != null) {
                addOperationSuppressedSafely(fatalFailure!!, failure)
            } else if (laterFailures.none { it === failure }) {
                laterFailures += failure
            }
        }

        if (failure is Error) {
            recordFatalLocked(failure)
        } else {
            fatalFailure?.let { addOperationSuppressedSafely(it, failure) }
        }
        return primaryFailure ?: failure
    }

    private fun recordFatalLocked(error: Error) {
        val currentFatal = fatalFailure
        if (currentFatal == null) {
            fatalFailure = error
            primaryFailure?.let { prior ->
                addOperationSuppressedSafely(error, prior)
                prior.suppressed.forEach { nested -> addOperationSuppressedSafely(error, nested) }
            }
            laterFailures.forEach { later -> addOperationSuppressedSafely(error, later) }
            laterFailures.clear()
        } else if (currentFatal !== error) {
            addOperationSuppressedSafely(currentFatal, error)
        }
        if (primaryFailure == null) primaryFailure = error
    }

}

internal fun addOperationSuppressedSafely(primary: Throwable, secondary: Throwable) {
    if (primary !== secondary && primary.suppressed.none { it === secondary }) {
        primary.addSuppressed(secondary)
    }
}
