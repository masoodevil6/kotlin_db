package gog.my_project.data_base.core.data_base

import gog.my_project.data_base.core.query.dialect.DialectQuery

/**
 * Composition root for one database's connection, pool, and SQL platform settings.
 *
 * The flat constructor, accessors, components, and copy method remain as a compatibility surface;
 * each delegates to the three configuration objects, which remain the only stored values.
 */
class DatabaseConfig(
    val connection: DatabaseConnectionConfig,
    val pool: DatabasePoolConfig = DatabasePoolConfig(),
    val platform: DatabasePlatformConfig = DatabasePlatformConfig(),
) {
    /** Compatibility constructor for the former flat DatabaseConfig API. */
    constructor(
        dbDomain: String,
        dbPort: Int?,
        dbName: String,
        dbUserName: String,
        dbPassword: String,
        dbPoolSize: Int = 10,
        dialect: DialectQuery = DialectQuery.MY_SQL,
        targetDatabaseVersion: ProductDatabaseVersion? = null,
    ) : this(
        connection = DatabaseConnectionConfig(dbDomain, dbPort, dbName, dbUserName, dbPassword),
        pool = DatabasePoolConfig(dbPoolSize),
        platform = DatabasePlatformConfig(dialect, targetDatabaseVersion),
    )

    @Deprecated("Use connection.dbDomain")
    val dbDomain: String get() = connection.dbDomain
    @Deprecated("Use connection.dbPort")
    val dbPort: Int? get() = connection.dbPort
    @Deprecated("Use connection.dbName")
    val dbName: String get() = connection.dbName
    @Deprecated("Use connection.dbUserName")
    val dbUserName: String get() = connection.dbUserName
    @Deprecated("Use connection.dbPassword")
    val dbPassword: String get() = connection.dbPassword
    @Deprecated("Use pool.maxPoolSize")
    val dbPoolSize: Int get() = pool.maxPoolSize
    @Deprecated("Use platform.dialect")
    val dialect: DialectQuery get() = platform.dialect
    @Deprecated("Use platform.targetDatabaseVersion")
    val targetDatabaseVersion: ProductDatabaseVersion? get() = platform.targetDatabaseVersion

    fun getDbUrl(): String = connection.getDbUrl()

    /** Compatibility copy method with the former flat parameter names. */
    fun copy(
        dbDomain: String = this.dbDomain,
        dbPort: Int? = this.dbPort,
        dbName: String = this.dbName,
        dbUserName: String = this.dbUserName,
        dbPassword: String = this.dbPassword,
        dbPoolSize: Int = this.dbPoolSize,
        dialect: DialectQuery = this.dialect,
        targetDatabaseVersion: ProductDatabaseVersion? = this.targetDatabaseVersion,
    ): DatabaseConfig = DatabaseConfig(
        dbDomain,
        dbPort,
        dbName,
        dbUserName,
        dbPassword,
        dbPoolSize,
        dialect,
        targetDatabaseVersion,
    )

    operator fun component1(): String = dbDomain
    operator fun component2(): Int? = dbPort
    operator fun component3(): String = dbName
    operator fun component4(): String = dbUserName
    operator fun component5(): String = dbPassword
    operator fun component6(): Int = dbPoolSize
    operator fun component7(): DialectQuery = dialect
    operator fun component8(): ProductDatabaseVersion? = targetDatabaseVersion

    override fun equals(other: Any?): Boolean = other is DatabaseConfig &&
        connection == other.connection && pool == other.pool && platform == other.platform

    override fun hashCode(): Int = 31 * (31 * connection.hashCode() + pool.hashCode()) + platform.hashCode()

    override fun toString(): String =
        "DatabaseConfig(dbDomain=$dbDomain, dbPort=$dbPort, dbName=$dbName, " +
            "dbUserName=$dbUserName, dbPassword=$dbPassword, dbPoolSize=$dbPoolSize, " +
            "dialect=$dialect, targetDatabaseVersion=$targetDatabaseVersion)"
}
