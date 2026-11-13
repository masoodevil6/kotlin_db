package gog.my_project.data_base.core.events

/** Common, public event contract shared by Operation and Migration observers. */
interface DatabaseEvent

/** Safe failure metadata for public event observers. Raw throwables and messages are excluded. */
data class DatabaseEventFailure(
    val typeName: String,
    val category: Category = Category.THROWABLE,
) {
    enum class Category {
        EXCEPTION,
        ERROR,
        THROWABLE,
    }

    init {
        require(typeName.isNotBlank()) { "Failure type name must not be blank" }
    }

    companion object {
        fun from(failure: Throwable): DatabaseEventFailure = DatabaseEventFailure(
            typeName = failure::class.qualifiedName
                ?: failure::class.simpleName
                ?: Throwable::class.qualifiedName.orEmpty(),
            category = when (failure) {
                is Error -> Category.ERROR
                is Exception -> Category.EXCEPTION
                else -> Category.THROWABLE
            },
        )
    }
}

/** Handle-scoped Watch registration. Closing it unregisters only this listener. */
fun interface DatabaseEventListener {
    fun onEvent(event: DatabaseEvent)
}

/** A Watch registration that can be closed without affecting other registrations. */
interface DatabaseWatchSubscription : AutoCloseable {
    override fun close()
}
