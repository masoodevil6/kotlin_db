package gog.my_project.data_base_manager.facade

/**
 * Consumer contract for a catalog of independently configured databases.
 *
 * The manager owns its registrations and lazily created database contexts. Obtaining a handle or
 * listing keys does not open a database connection. Every handle operation admitted before
 * shutdown begins is allowed to finish; shutdown rejects later operations, waits for admitted
 * work, and then closes contexts created and owned by this manager. No JDBC connection is exposed
 * by this contract.
 *
 * Concurrent operations are admitted independently. Migration runs are serialized per database
 * registration; operations on separate registrations are not serialized by a global database
 * lock. The manager and its handles must not be used after shutdown has completed.
 */
interface DatabaseManagerApi : AutoCloseable {
    /**
     * Returns a handle for the registration whose [DatabaseKey] is equal to [key]. Equality and
     * hashing follow the key's `equals` and `hashCode` implementations.
     *
     * This lookup does not initialize the database context. Throws [NoSuchElementException] when
     * the key is not registered and [IllegalStateException] after shutdown admission has closed.
     */
    operator fun get(key: DatabaseKey): DatabaseHandleApi

    /**
     * Returns a snapshot of typed registration keys in catalog insertion order.
     *
     * This method does not initialize database contexts. It throws [IllegalStateException] once
     * shutdown has started.
     */
    fun registeredKeys(): Set<DatabaseKey>

    /**
     * Stops accepting new handle operations, waits for accepted operations, and closes all
     * manager-owned contexts that were initialized. A failure from closing an owned context is
     * propagated after the remaining contexts have also been closed. A call made while another
     * shutdown is in progress waits for it and receives its failure, if any; a call after shutdown
     * has completed is a no-op.
     *
     * Calling this from inside an active operation on this manager is rejected to avoid waiting
     * for the caller's own operation lease.
     */
    fun shutdown()

    /** Closes the manager by delegating to [shutdown]. */
    override fun close()
}
