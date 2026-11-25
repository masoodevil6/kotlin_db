package gog.my_project.data_base_manager.execute.tools

sealed class ExecuteResult<out T> {

    data class Success<out T>(val result: T?) : ExecuteResult<T>();

    data class Failure(val exception: Throwable) : ExecuteResult<Nothing>();

    /**
     * Transforms a non-null successful payload and preserves null success or failure.
     * A transformation exception is allowed to propagate unchanged.
     */
    fun <R> success(transform: (@UnsafeVariance T) -> R): ExecuteResult<R> = when (this) {
        is Success -> {
            val value = result
            if (value == null) {
                Success<R>(null)
            } else {
                Success(transform(value))
            }
        }

        is Failure -> this
    }

    /**
     * Observes a failure without consuming or recovering it, then returns this result.
     * A handler exception is allowed to propagate unchanged.
     */
    fun error(handler: (Throwable) -> Unit): ExecuteResult<T> {
        if (this is Failure) {
            handler(exception)
        }
        return this
    }

    /**
     * Extracts a successful non-null value. A failure rethrows its original throwable;
     * a successful result without a value is a contract error.
     */
    fun toValue(): T = when (this) {
        is Success -> result
            ?: throw IllegalStateException("Successful result contains no value")

        is Failure -> throw exception
    }

}
