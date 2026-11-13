package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_manager.connection.manager.DatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.internal.DatabaseManagerConfiguration
import gog.my_project.data_base_manager.internal.DatabaseRegistration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Concrete catalog of independent database registrations and owner of their lazily-created
 * contexts. Prefer [DatabaseManagerApi] in consumer-facing parameter and property types; this
 * class remains constructible for the existing Kotlin and Java API.
 */
class DatabaseManager private constructor(
    private val configuration: DatabaseManagerConfiguration,
    private val contextFactory: (String, DatabaseConfig, DatabaseIdentity?) -> DatabaseContext,
) : DatabaseManagerApi {
    private enum class State { OPEN, CLOSING, CLOSED }

    private val lifecycleLock = ReentrantLock()
    private val lifecycleChanged = lifecycleLock.newCondition()
    private val registrations = LinkedHashMap<DatabaseKey, DatabaseRegistration>()

    /** Compatibility store for the former String-based registration API. */
    private val legacyContexts = LinkedHashMap<String, DatabaseContext>()

    private var state = State.OPEN
    private var activeOperations = 0
    private var shutdownFailure: Throwable? = null
    private val operationDepth = ThreadLocal.withInitial { 0 }

    init {
        configuration.registrations.forEach { registration ->
            require(registrations.putIfAbsent(registration.key, registration) == null) {
                "Database key '${registration.key}' (type '${registration.key::class.qualifiedName}') is already registered"
            }
        }
    }

    /** Creates an empty manager for the deprecated String-based API. */
    constructor() : this(
        DatabaseManagerConfiguration(emptyList()),
        { name, config, identity ->
            DatabaseContext(name, config, DatabaseConnection(config.connection), identity)
        },
    )

    /** Builds an immutable typed database catalog. */
    constructor(block: DatabaseCatalogBuilder.() -> Unit) : this(
        DatabaseCatalogBuilder().apply(block).snapshot(),
        { name, config, identity ->
            DatabaseContext(name, config, DatabaseConnection(config.connection), identity)
        },
    )

    /** Existing internal lifecycle-test seam retained with its original signature. */
    internal constructor(
        block: DatabaseCatalogBuilder.() -> Unit,
        contextFactory: (String, DatabaseConfig) -> DatabaseContext,
    ) : this(
        DatabaseCatalogBuilder().apply(block).snapshot(),
        { name, config, _ -> contextFactory(name, config) },
    )

    /** Identity-aware seam used to verify Registration-to-Context propagation. */
    internal constructor(
        block: DatabaseCatalogBuilder.() -> Unit,
        contextFactory: (String, DatabaseConfig, DatabaseIdentity?) -> DatabaseContext,
    ) : this(DatabaseCatalogBuilder().apply(block).snapshot(), contextFactory)

    /** Returns a handle for the registration equal by [DatabaseKey.equals] to [key]. */
    override operator fun get(key: DatabaseKey): DatabaseHandle = handleFor(key)

    private fun handleFor(key: DatabaseKey): DatabaseHandle = lifecycleLock.withLock {
        ensureOpen()
        val registration = registrations[key] ?: throw NoSuchElementException(
            "Database key '$key' (type '${key::class.qualifiedName}') is not registered",
        )
        DatabaseHandle.managed(
            registration.key,
            HandleOperationRunner(registration),
            registration.attributes,
        )
    }

    /** Returns registered value keys in catalog insertion order. */
    override fun registeredKeys(): Set<DatabaseKey> = lifecycleLock.withLock {
        ensureOpen()
        registrations.keys.toSet()
    }

    /**
     * Legacy String registration retained for source and binary compatibility. New code should
     * use [DatabaseCatalogBuilder.database] and a [DatabaseKey].
     */
    @Deprecated("Use DatabaseManager { database(DatabaseKey) { config = ... } }")
    fun register(name: String, config: DatabaseConfig): DatabaseContext = lifecycleLock.withLock {
        ensureOpen()
        require(name.isNotBlank()) { "Database name must not be blank" }
        check(!legacyContexts.containsKey(name)) { "Database '$name' is already registered" }
        DatabaseContext(name, config).also { legacyContexts[name] = it }
    }

    /** Legacy String lookup retained for compatibility. */
    @Deprecated("Use get(DatabaseKey) to obtain a DatabaseHandle")
    fun select(name: String): DatabaseContext = lifecycleLock.withLock {
        ensureOpen()
        requireNotNull(legacyContexts[name]) { "Database '$name' is not registered" }
    }

    /** Names belong only to legacy String registrations. */
    @Deprecated("Use registeredKeys()")
    fun registeredNames(): Set<String> = lifecycleLock.withLock {
        ensureOpen()
        legacyContexts.keys.toSortedSet()
    }

    /**
     * Stops admission, waits for accepted handle operations to finish, then closes every owned
     * context. The condition wait releases the Manager lock, so unrelated operations are never
     * serialized behind shutdown or held under the lock while running SQL or user callbacks.
     */
    override fun shutdown() {
        check(operationDepth.get() == 0) {
            "DatabaseManager.shutdown() cannot be called from an active DatabaseHandle operation"
        }

        val contexts = lifecycleLock.withLock {
            when (state) {
                State.CLOSED -> return
                State.CLOSING -> {
                    while (state != State.CLOSED) lifecycleChanged.awaitUninterruptibly()
                    shutdownFailure?.let { throw it }
                    return
                }
                State.OPEN -> state = State.CLOSING
            }

            while (activeOperations != 0) lifecycleChanged.awaitUninterruptibly()

            val ownedContexts = (registrations.values.mapNotNull { it.context } + legacyContexts.values).distinct()
            registrations.values.forEach { it.context = null }
            registrations.clear()
            legacyContexts.clear()
            ownedContexts
        }

        var failure: Throwable? = null
        try {
            contexts.forEach { context ->
                try {
                    context.close()
                } catch (error: Throwable) {
                    if (failure == null) failure = error
                    else if (failure !== error) {
                        try {
                            failure?.addSuppressed(error)
                        } catch (_: Throwable) {
                            // Continue closing all remaining manager-owned contexts.
                        }
                    }
                }
            }
        } finally {
            lifecycleLock.withLock {
                shutdownFailure = failure
                state = State.CLOSED
                lifecycleChanged.signalAll()
            }
        }
        failure?.let { throw it }
    }

    override fun close() = shutdown()

    private fun contextFor(registration: DatabaseRegistration): DatabaseContext = synchronized(registration) {
        registration.context ?: contextFactory(
            registration.key::class.qualifiedName ?: "database",
            registration.config,
            registration.lockIdentity,
        ).also { registration.context = it }
    }

    private fun ensureOpen() {
        check(state == State.OPEN) { "DatabaseManager is shutting down or has been shut down" }
    }

    private inline fun <T> withOperationLease(block: () -> T): T {
        lifecycleLock.withLock {
            ensureOpen()
            activeOperations++
            operationDepth.set(operationDepth.get() + 1)
        }
        try {
            return block()
        } finally {
            val depth = operationDepth.get() - 1
            if (depth == 0) operationDepth.remove() else operationDepth.set(depth)
            lifecycleLock.withLock {
                activeOperations--
                check(activeOperations >= 0) { "DatabaseManager operation lease underflow" }
                if (activeOperations == 0) lifecycleChanged.signalAll()
            }
        }
    }

    private inner class HandleOperationRunner(
        private val registration: DatabaseRegistration,
    ) : DatabaseHandleOperationRunner {
        override fun <T> run(block: (() -> DatabaseContext) -> T): T =
            withOperationLease { block { contextFor(registration) } }

    }
}
