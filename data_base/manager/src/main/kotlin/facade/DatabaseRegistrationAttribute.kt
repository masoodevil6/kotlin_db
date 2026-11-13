package gog.my_project.data_base_manager.facade

/**
 * A typed key for one value attached to a database Registration.
 *
 * Keep the key instance in a stable property and use that same instance when registering and
 * retrieving the value. Keys use object identity, so two keys with the same value type remain
 * independent. The private constructor and typed registration/retrieval methods keep the value
 * type associated with its key; the only erased cast is encapsulated inside this class. The
 * Registration snapshots key/value associations; values are retained by reference, so callers
 * should supply immutable value objects.
 */
class DatabaseRegistrationAttribute<T : Any> private constructor() {
    @Suppress("UNCHECKED_CAST")
    internal fun checkedValue(value: Any): T = value as T

    companion object {
        /** Creates a distinct typed key. Keep and reuse the returned key instance. */
        @JvmStatic
        fun <T : Any> create(): DatabaseRegistrationAttribute<T> = DatabaseRegistrationAttribute()
    }
}

/**
 * Optional capability implemented by Manager-created handles to read values belonging to their
 * exact Registration. Missing keys return `null`. Existing [DatabaseHandleApi] implementations
 * do not need to implement this capability.
 */
interface DatabaseHandleAttributes {
    fun <T : Any> attribute(key: DatabaseRegistrationAttribute<T>): T?
}
