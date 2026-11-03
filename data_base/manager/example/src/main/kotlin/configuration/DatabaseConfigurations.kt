package gog.my_project.data_base_manager.example.configuration

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DatabaseConnectionConfig
import gog.my_project.data_base.core.data_base.DatabasePlatformConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery

/** Creates one independent configuration from environment values; no connection is opened here. */
fun databaseConfig(prefix: String, defaultDatabaseName: String): DatabaseConfig = DatabaseConfig(
    connection = DatabaseConnectionConfig(
        dbDomain = System.getenv("${prefix}_DB_DOMAIN") ?: "jdbc:mysql://127.0.0.1",
        dbPort = System.getenv("${prefix}_DB_PORT")?.toIntOrNull() ?: 3306,
        dbName = System.getenv("${prefix}_DB_NAME") ?: defaultDatabaseName,
        dbUserName = System.getenv("${prefix}_DB_USER") ?: "example-user",
        dbPassword = System.getenv("${prefix}_DB_PASSWORD") ?: "",
    ),
    platform = DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
)

/** Require explicit credentials before any opt-in example can reach a database. */
fun requireDatabaseExampleEnvironment() {
    listOf("MAIN", "ANALYTICS").forEach { prefix ->
        listOf("DB_NAME", "DB_USER", "DB_PASSWORD").forEach { suffix ->
            require(!System.getenv("${prefix}_$suffix").isNullOrBlank()) {
                "Set ${prefix}_$suffix before running database examples"
            }
        }
        System.getenv("${prefix}_DB_PORT")?.let { port ->
            require(port.toIntOrNull() in 1..65535) { "${prefix}_DB_PORT must be between 1 and 65535" }
        }
    }
}
