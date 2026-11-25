package gog.my_project.data_base_session.example

import java.sql.Connection
import java.sql.DriverManager

object DatabaseConnectionFactory {
    fun openMySqlConnection(): Connection {
        val host = requiredEnvironmentVariable("DB_HOST")
        val port = requiredEnvironmentVariable("DB_PORT").toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: throw IllegalStateException("DB_PORT must be an integer between 1 and 65535.")
        val database = requiredEnvironmentVariable("DB_NAME")
        val username = requiredEnvironmentVariable("DB_USER")
        val password = System.getenv("DB_PASSWORD")
            ?: throw IllegalStateException("Missing required environment variable DB_PASSWORD.")

        val jdbcUrl = "jdbc:mysql://$host:$port/$database"
        return DriverManager.getConnection(jdbcUrl, username, password)
    }

    private fun requiredEnvironmentVariable(name: String): String =
        System.getenv(name)
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Missing required environment variable $name.")
}
