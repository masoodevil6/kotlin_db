package gog.my_project.data_base.query.executer.result

import gog.my_project.collection.ICollection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import kotlin.jvm.JvmName

/** Extracts an already-produced typed collection, throwing if the result is not a value. */
fun <T> ExecuteResult<ICollection<T>>.toCollection(): ICollection<T> = when (this) {
    is ExecuteResult.Success -> result
        ?: throw IllegalStateException("Successful result contains no collection")

    is ExecuteResult.Failure -> throw exception
}

/** Extracts raw SELECT rows without rerunning the query or replacing the returned list. */
@JvmName("toListFromRawRows")
fun <T> ExecuteResult<List<T>>.toList(): List<T> = toValue()

/** Converts an already materialized typed collection to a Kotlin List. */
@JvmName("toListFromTypedCollection")
fun <T> ExecuteResult<ICollection<T>>.toList(): List<T> = toCollection().toList()
