package gog.my_project.data_base.core.data_base

import gog.my_project.data_base.core.query.dialect.DialectQuery

object DefaultDatabaseConfig {

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
