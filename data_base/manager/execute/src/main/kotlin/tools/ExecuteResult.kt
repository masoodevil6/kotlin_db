package gog.my_project.data_base.manager.execute.tools

sealed class ExecuteResult<out T> {

    data class Success<out T>(val result: T?) : ExecuteResult<T>();

    data class Failure(val exception: Throwable) : ExecuteResult<Nothing>();

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
