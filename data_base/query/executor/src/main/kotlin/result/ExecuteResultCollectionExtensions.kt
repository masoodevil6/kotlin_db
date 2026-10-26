package gog.my_project.data_base.query.executer.result

import gog.my_project.collection.ICollection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

/** Extracts an already-produced typed collection, throwing if the result is not a value. */
fun <T> ExecuteResult<ICollection<T>>.toCollection(): ICollection<T> = when (this) {
    is ExecuteResult.Success -> result
        ?: throw IllegalStateException("Successful result contains no collection")

    is ExecuteResult.Failure -> throw exception
}
