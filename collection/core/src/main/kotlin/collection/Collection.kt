package gog.my_project.collection

class Collection<T> : ICollection<T> {
    private val elements: MutableList<T> = mutableListOf()

    override fun add(element: T) {
        elements.add(element)
    }

    override fun forEach(action: (T) -> Unit) {
        elements.forEach(action)
    }

    override fun filter(predicate: (T) -> Boolean): ICollection<T> {
        val filtered = Collection<T>()
        elements.forEach { element ->
            if (predicate(element)) {
                filtered.add(element)
            }
        }
        return filtered
    }

    override fun <R> map(transform: (T) -> R): ICollection<R> {
        val mapped = Collection<R>()
        elements.forEach { element ->
            mapped.add(transform(element))
        }
        return mapped
    }

    override fun firstOrNull(predicate: (T) -> Boolean): T? = elements.firstOrNull(predicate)

    override fun toList(): List<T> = elements.toList()

    override operator fun get(index: Int): T = elements[index]

    override val size: Int
        get() = elements.size
}
