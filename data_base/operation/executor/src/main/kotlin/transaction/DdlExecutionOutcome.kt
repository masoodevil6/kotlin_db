package gog.my_project.data_base_operation.executor.transaction

/** Whether a failed DDL statement must fail its enclosing Operation scope. */
enum class DdlFailureDisposition {
    FATAL,
    RECOVERABLE,
}

/** Classifies one failed DDL execution without coupling Operation to a database dialect. */
fun interface DdlFailurePolicy {
    fun classify(failure: Throwable): DdlFailureDisposition
}

/** Result of executing DDL with an explicit failure policy. */
sealed interface DdlExecutionOutcome {
    data class Success(
        val value: Boolean,
    ) : DdlExecutionOutcome

    data class Failure(
        val exception: Throwable,
        val disposition: DdlFailureDisposition,
    ) : DdlExecutionOutcome
}
