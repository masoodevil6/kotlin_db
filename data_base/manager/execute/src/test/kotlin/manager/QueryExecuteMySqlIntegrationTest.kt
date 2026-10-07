package gog.my_project.data_base.manager.execute.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.DriverManager
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Opt-in, read-only MySQL integration test for JDBC parameter type round-trips. */
class QueryExecuteMySqlIntegrationTest {
    @Test
    fun bindsSupportedTypesAndRoundTripsWithConfiguredMySqlDriver() {
        val host = System.getProperty("execute.test.db.host")
        val database = System.getProperty("execute.test.db.name")
        val username = System.getProperty("execute.test.db.username")
        val password = System.getProperty("execute.test.db.password")
        assumeTrue(!host.isNullOrBlank() && !database.isNullOrBlank() && !username.isNullOrBlank()) {
            "Set execute.test.db.host, execute.test.db.name, and execute.test.db.username Gradle project properties to enable MySQL binding integration"
        }

        val port = System.getProperty("execute.test.db.port")?.toIntOrNull() ?: 3306
        val config = DatabaseConfig(
            dbDomain = "jdbc:mysql://$host",
            dbPort = port,
            dbName = "$database?serverTimezone=UTC",
            dbUserName = username!!,
            dbPassword = password.orEmpty(),
        )
        val previousConfig = DefaultDatabaseConfig.config
        DefaultDatabaseConfig.config = config
        try {
            // Confirm the configured Connector/J can acquire a connection before running the query.
            DriverManager.getConnection(config.getDbUrl(), config.dbUserName, config.dbPassword).use { }

            val localDate = LocalDate.of(2026, 10, 5)
            val localTime = LocalTime.of(12, 30, 0)
            val localDateTime = LocalDateTime.of(2026, 10, 5, 12, 30, 0)
            val instant = Instant.parse("2026-10-05T12:30:00Z")
            val query = BuiltQuery(
                "SELECT :intValue AS int_value, :longValue AS long_value, " +
                    ":doubleValue AS double_value, :floatValue AS float_value, " +
                    ":booleanValue AS boolean_value, :stringValue AS string_value, " +
                    ":dateValue AS date_value, :timeValue AS time_value, " +
                    "CAST(:dateTimeValue AS DATETIME) AS datetime_value, :instantValue AS instant_value, " +
                    ":nullValue AS null_value",
                mutableListOf(
                    SqlParameter.of("intValue", 17),
                    SqlParameter.of("longValue", 9_223_372_036_854_770_000L),
                    SqlParameter.of("doubleValue", 3.25),
                    SqlParameter.of("floatValue", 1.5f),
                    SqlParameter.of("booleanValue", true),
                    SqlParameter.of("stringValue", "query-builder"),
                    SqlParameter.of("dateValue", localDate),
                    SqlParameter.of("timeValue", localTime),
                    SqlParameter.of("dateTimeValue", localDateTime),
                    SqlParameter.of("instantValue", instant),
                    SqlParameter.nullOf("nullValue", Types.VARCHAR),
                ),
            )

            var callbackCount = 0
            QueryExecute().executeSelect(query) { result ->
                callbackCount++
                when (result) {
                    is ExecuteResult.Failure -> throw AssertionError("MySQL binding failed", result.exception)
                    is ExecuteResult.Success -> {
                        val rows = assertNotNull(result.result)
                        rows.use {
                            assertTrue(it.next())
                            assertEquals(17, it.getInt("int_value"))
                            assertEquals(9_223_372_036_854_770_000L, it.getLong("long_value"))
                            assertEquals(3.25, it.getDouble("double_value"))
                            assertEquals(1.5f, it.getFloat("float_value"))
                            assertEquals(true, it.getBoolean("boolean_value"))
                            assertEquals("query-builder", it.getString("string_value"))
                            assertEquals(localDate, it.getDate("date_value").toLocalDate())
                            assertEquals(localTime, it.getTime("time_value").toLocalTime())
                            assertEquals(localDateTime, it.getObject("datetime_value", LocalDateTime::class.java))
                            assertEquals(instant, it.getTimestamp("instant_value").toInstant())
                            assertNull(it.getObject("null_value"))
                        }
                    }
                }
            }
            assertEquals(1, callbackCount)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }
}
