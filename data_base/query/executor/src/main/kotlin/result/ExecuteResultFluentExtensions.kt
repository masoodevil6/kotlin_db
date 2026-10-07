package gog.my_project.data_base.query.executer.result

import gog.my_project.data_base.manager.execute.tools.ExecuteResult

/** Transforms a non-null successful payload and preserves null success or failure. */
fun <T, R> ExecuteResult<T>.success(
    transform: (T) -> R,
): ExecuteResult<R> = when (this) {
    is ExecuteResult.Success -> {
        val value = result
        if (value == null) {
            ExecuteResult.Success<R>(null)
        } else {
            ExecuteResult.Success(transform(value))
        }
    }
    is ExecuteResult.Failure -> this
}

/** Observes a failure without consuming or recovering it. */
fun <T> ExecuteResult<T>.error(
    handler: (Throwable) -> Unit,
): ExecuteResult<T> {
    if (this is ExecuteResult.Failure) {
        handler(exception)
    }
    return this
}
