package gog.my_project.collection

interface ICollection<T> {
    fun add(element: T)

    fun forEach(action: (T) -> Unit)

    fun filter(predicate: (T) -> Boolean): ICollection<T>

    fun <R> map(transform: (T) -> R): ICollection<R>

    fun firstOrNull(predicate: (T) -> Boolean): T?

    fun toList(): List<T>

    operator fun get(index: Int): T

    val size: Int
}
