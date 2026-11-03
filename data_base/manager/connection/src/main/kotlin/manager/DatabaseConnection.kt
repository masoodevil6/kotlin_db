package gog.my_project.data_base_manager.connection.manager

import gog.my_project.data_base.core.data_base.DatabaseConnectionConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseConnection(
    @Suppress("DEPRECATION")
    private val config: DatabaseConnectionConfig = DefaultDatabaseConfig.config.connection,
) : IDatabaseConnection {

    override fun build(): Connection {
        return try {
            DriverManager.getConnection(
                config.getDbUrl(),
                config.dbUserName,
                config.dbPassword,
            )
        }
        catch (e: SQLException) {
            throw RuntimeException(
                "Cannot connect to database at ${config.getDbUrl()} as ${config.dbUserName}",
                e,
            )
        }
    }


}
