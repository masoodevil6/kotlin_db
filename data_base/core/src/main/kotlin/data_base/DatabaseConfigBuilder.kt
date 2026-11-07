package gog.my_project.data_base_operation.config.data_base

class DatabaseConfigBuilder {
    private var connection = DatabaseConnectionConfig("", null, "", "", "")
    private var pool = DatabasePoolConfig()
    private var platform = DatabasePlatformConfig()

    fun connection(value: DatabaseConnectionConfig) = apply { connection = value }
    fun pool(value: DatabasePoolConfig) = apply { pool = value }
    fun platform(value: DatabasePlatformConfig) = apply { platform = value }

    /** Compatibility setters for the former flat builder API. */
    fun domain(value: String) = apply { connection = connection.copy(dbDomain = value) }
    fun port(value: Int) = apply { connection = connection.copy(dbPort = value) }
    fun name(value: String) = apply { connection = connection.copy(dbName = value) }
    fun username(value: String) = apply { connection = connection.copy(dbUserName = value) }
    fun password(value: String) = apply { connection = connection.copy(dbPassword = value) }
    fun poolSize(value: Int) = apply { pool = pool.copy(maxPoolSize = value) }
    fun dialect(value: gog.my_project.data_base_operation.config.query.dialect.DialectQuery) =
        apply { platform = platform.copy(dialect = value) }
    fun targetDatabaseVersion(value: ProductDatabaseVersion?) =
        apply { platform = platform.copy(targetDatabaseVersion = value) }

    fun build() = DatabaseConfig(connection = connection, pool = pool, platform = platform)
}
