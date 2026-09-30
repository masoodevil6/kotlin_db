package gog.my_project.data_base.manager.connection.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.manager.connection.interfaces.IDatabaseConnection
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseConnection() : IDatabaseConnection {

    override fun build(): Connection {
        return try {
            DriverManager.getConnection(
                DefaultDatabaseConfig.config.getDbUrl(),
                DefaultDatabaseConfig.config.dbUserName ,
                DefaultDatabaseConfig.config.dbPassword
            )
        }
        catch (e: SQLException) {
            throw RuntimeException(
                "Cannot connect to database at ${DefaultDatabaseConfig.config.getDbUrl()} " +
                    "as ${DefaultDatabaseConfig.config.dbUserName}",
                e,
            )
        }
    }


}