package gog.my_project.data_base_manager.connection.manager

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DefaultDatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePoolConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import java.sql.Connection
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * One selected database's immutable configuration and connection provider.
 * The current provider opens independent JDBC connections; configured pool size is retained for
 * future pool implementations but does not create or manage a pool by itself.
 */
class DatabaseContext private constructor(
    val name: String,
    val config: DatabaseConfig,
    private val connectionProvider: IDatabaseConnection = DatabaseConnection(config.connection),
    /** Configured-destination identity captured by registration; absent for legacy/manual contexts. */
    val databaseIdentity: DatabaseIdentity?,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit,
) : AutoCloseable {

    /** Retains the established constructor; unregistered contexts have no registration identity. */
    constructor(
        name: String,
        config: DatabaseConfig,
        connectionProvider: IDatabaseConnection = DatabaseConnection(config.connection),
    ) : this(
        name,
        config,
        connectionProvider,
        null,
        Unit,
    )

    /** Creates a Context with the immutable identity already captured by its Database registration. */
    constructor(
        name: String,
        config: DatabaseConfig,
        connectionProvider: IDatabaseConnection,
        databaseIdentity: DatabaseIdentity?,
    ) : this(name, config, connectionProvider, databaseIdentity, Unit)

    private val lifecycleLock = ReentrantReadWriteLock()
    private var closed = false

    val connectionConfig: DatabaseConnectionConfig get() = config.connection
    val poolConfig: DatabasePoolConfig get() = config.pool
    val platformConfig: DatabasePlatformConfig get() = config.platform

    fun openConnection(): Connection = lifecycleLock.read {
        check(!closed) { "DatabaseContext '$name' has been closed" }
        connectionProvider.build()
    }

    /** Invalidates this context and closes a provider that explicitly owns closeable resources. */
    override fun close() = lifecycleLock.write {
        if (closed) return@write
        closed = true
        (connectionProvider as? AutoCloseable)?.close()
    }

    companion object {
        /** Captures the legacy global configuration for existing no-argument entry points. */
        @Suppress("DEPRECATION")
        fun legacyDefault(): DatabaseContext = DatabaseContext("default", DefaultDatabaseConfig.config)
    }
}
