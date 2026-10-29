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

/**
 * Continues a successful result with another result-producing operation.
 * Unlike [success], this flattens the returned result, so a deliberate Failure
 * remains a Failure for callers such as transaction scopes. A successful null
 * payload is passed to [transform] so the caller can decide how to handle it.
 */
fun <T, R> ExecuteResult<T>.then(
    transform: (T?) -> ExecuteResult<R>,
): ExecuteResult<R> = when (this) {
    is ExecuteResult.Success -> transform(result)
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


operator fun <T> ExecuteResult<List<T>>.get(index: Int): T {
    return when (this) {
        is ExecuteResult.Success -> {
            result?.get(index)
                ?: throw IllegalStateException("Successful result contains no data")
        }

        is ExecuteResult.Failure -> {
            throw exception
        }
    }
}
