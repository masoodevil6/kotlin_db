package gog.my_project.data_base_manager.connection.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.data_base.DatabaseConnectionConfig
import gog.my_project.data_base.core.data_base.DatabasePoolConfig
import gog.my_project.data_base.core.data_base.DatabasePlatformConfig
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
class DatabaseContext(
    val name: String,
    val config: DatabaseConfig,
    private val connectionProvider: IDatabaseConnection = DatabaseConnection(config.connection),
) : AutoCloseable {
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
