package gog.my_project.data_base_operation.config.data_base

import gog.my_project.data_base_operation.config.query.dialect.DialectQuery

/** Compatibility bridge for applications that still configure a single global database. */
object DefaultDatabaseConfig {

    @Deprecated("Register a DatabaseConfig with DatabaseManager and pass its DatabaseContext")
    var config = DatabaseConfig(
        dbDomain = "jdbc:mysql://127.0.0.1",
        dbPort = 3306,
        dbName = "",
        dbUserName = "root",
        dbPassword = "",
        dbPoolSize = 10
    )

    @Deprecated("Set dialect on DatabaseConfig / DatabaseConfigBuilder")
    var dialect: DialectQuery
        get() = config.dialect
        set(value) { config = config.copy(dialect = value) }

}
