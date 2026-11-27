package gog.my_project.data_base_operation.executor.transaction

/** Semantic result of a synchronous Operation lock bracket. */
sealed interface OperationLockResult<out T> {
    /** The callback completed successfully while the lock was held. */
    data class Acquired<out T>(val value: T?) : OperationLockResult<T>

    /** The backend confirmed that the lock was not acquired before the wait policy expired. */
    data object TimedOut : OperationLockResult<Nothing>
}
