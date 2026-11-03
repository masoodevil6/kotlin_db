package gog.my_project.data_base_manager.facade

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.internal.DatabaseRegistration

/** Catalog of independent database registrations and owner of their lazily-created contexts. */
class DatabaseManager private constructor(
    initialRegistrations: List<DatabaseRegistration>,
    private val contextFactory: (String, DatabaseConfig) -> DatabaseContext,
) : AutoCloseable {
    private val lifecycleLock = Any()
    private val registrations = LinkedHashMap<DatabaseKey, DatabaseRegistration>()

    /** Compatibility store for the former String-based registration API. */
    private val legacyContexts = LinkedHashMap<String, DatabaseContext>()

    @Volatile
    private var closed = false

    init {
        initialRegistrations.forEach { registration ->
            require(registrations.putIfAbsent(registration.key, registration) == null) {
                "Database key '${registration.key::class.qualifiedName}' is already registered"
            }
        }
    }

    /** Creates an empty manager for the deprecated String-based API. */
    constructor() : this(emptyList(), { name, config -> DatabaseContext(name, config) })

    /** Builds an immutable typed database catalog. */
    constructor(block: DatabaseCatalogBuilder.() -> Unit) : this(
        DatabaseCatalogBuilder().apply(block).snapshot(),
        { name, config -> DatabaseContext(name, config) },
    )

    /** Internal seam for lifecycle tests; production registrations use DatabaseContext directly. */
    internal constructor(
        block: DatabaseCatalogBuilder.() -> Unit,
        contextFactory: (String, DatabaseConfig) -> DatabaseContext,
    ) : this(DatabaseCatalogBuilder().apply(block).snapshot(), contextFactory)

    /** Returns a handle only for a key present in this manager's catalog. */
    operator fun get(key: DatabaseKey): DatabaseHandle = handleFor(key)

    private fun handleFor(key: DatabaseKey): DatabaseHandle = synchronized(lifecycleLock) {
        ensureOpen()
        val registration = registrations[key] ?: throw NoSuchElementException(
            "Database key '${key::class.qualifiedName}' is not registered",
        )
        DatabaseHandle(key, { contextFor(registration) }, registration.migrations)
    }

    fun registeredKeys(): Set<DatabaseKey> = synchronized(lifecycleLock) {
        ensureOpen()
        registrations.keys.toSet()
    }

    /**
     * Legacy String registration retained for source and binary compatibility. New code should
     * use [DatabaseCatalogBuilder.database] and a [DatabaseKey].
     */
    @Deprecated("Use DatabaseManager { database(DatabaseKey) { config = ... } }")
    fun register(name: String, config: DatabaseConfig): DatabaseContext = synchronized(lifecycleLock) {
        ensureOpen()
        require(name.isNotBlank()) { "Database name must not be blank" }
        check(!legacyContexts.containsKey(name)) { "Database '$name' is already registered" }
        DatabaseContext(name, config).also { legacyContexts[name] = it }
    }

    /** Legacy String lookup retained for compatibility. */
    @Deprecated("Use get(DatabaseKey) to obtain a DatabaseHandle")
    fun select(name: String): DatabaseContext = synchronized(lifecycleLock) {
        ensureOpen()
        requireNotNull(legacyContexts[name]) { "Database '$name' is not registered" }
    }

    /** Names belong only to legacy String registrations. */
    @Deprecated("Use registeredKeys()")
    fun registeredNames(): Set<String> = synchronized(lifecycleLock) {
        ensureOpen()
        legacyContexts.keys.toSortedSet()
    }

    /** Prevents new handle access and releases manager-owned cached contexts. Safe to call repeatedly. */
    fun shutdown() {
        val contexts = synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            val ownedContexts = registrations.values.mapNotNull { it.context } + legacyContexts.values
            registrations.values.forEach { it.context = null }
            registrations.clear()
            legacyContexts.clear()
            ownedContexts
        }

        var failure: Throwable? = null
        contexts.forEach { context ->
            try {
                context.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure?.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    override fun close() = shutdown()

    private fun contextFor(registration: DatabaseRegistration): DatabaseContext = synchronized(registration) {
        val cached = synchronized(lifecycleLock) {
            ensureOpen()
            registration.context
        }
        if (cached != null) {
            cached
        } else {
            val created = contextFactory(
                registration.key::class.qualifiedName ?: "database",
                registration.config,
            )
            try {
                val installed = synchronized(lifecycleLock) {
                    ensureOpen()
                    registration.context ?: created.also { registration.context = it }
                }
                if (installed !== created) created.close()
                installed
            } catch (failure: Throwable) {
                try {
                    created.close()
                } catch (closeFailure: Throwable) {
                    if (failure !== closeFailure) failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        }
    }

    private fun ensureOpen() {
        check(!closed) { "DatabaseManager has been shut down" }
    }
}
