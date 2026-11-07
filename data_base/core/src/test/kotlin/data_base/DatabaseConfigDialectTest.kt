package gog.my_project.data_base_operation.config.data_base

import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseConfigDialectTest {
    @Test
    fun builderKeepsDialectAndTargetVersionAsSeparateSettings() {
        val config = DatabaseConfigBuilder()
            .dialect(DialectQuery.MARIA_DB)
            .targetDatabaseVersion(MARIA_DB.Version(10, 4, 28))
            .build()

        assertEquals(DialectQuery.MARIA_DB, config.platform.dialect)
        assertEquals(MARIA_DB.Version(10, 4, 28), config.platform.targetDatabaseVersion)
        assertEquals(10, config.pool.maxPoolSize)
    }

    @Test
    @Suppress("DEPRECATION")
    fun flatCompatibilityConstructorAndCopyDelegateToSeparatedConfig() {
        val config = DatabaseConfig(
            dbDomain = "jdbc:mysql://localhost",
            dbPort = 3307,
            dbName = "legacy",
            dbUserName = "user",
            dbPassword = "secret",
            dbPoolSize = 4,
        ).copy(dialect = DialectQuery.MARIA_DB)

        assertEquals("jdbc:mysql://localhost:3307/legacy", config.getDbUrl())
        assertEquals("legacy", config.dbName)
        assertEquals(4, config.pool.maxPoolSize)
        assertEquals(DialectQuery.MARIA_DB, config.platform.dialect)
        assertEquals(
            DatabaseConnectionConfig("jdbc:mysql://localhost", 3307, "legacy", "user", "secret"),
            config.connection,
        )
    }

    @Test
    fun configurationDiagnosticStringsNeverExposeDatabasePassword() {
        val password = "unique-password-must-not-leak-9c21"
        val connection = DatabaseConnectionConfig(
            dbDomain = "jdbc:mysql://db.internal",
            dbPort = 3306,
            dbName = "customer_data",
            dbUserName = "app_user",
            dbPassword = password,
        )
        val config = DatabaseConfig(connection = connection)
        val builderConfig = DatabaseConfigBuilder().connection(connection).build()

        listOf(
            connection.toString(),
            config.toString(),
            config.connection.toString(),
            builderConfig.toString(),
        ).forEach { diagnostic ->
            assertTrue(password !in diagnostic, "Password leaked in diagnostic string: $diagnostic")
        }

        assertTrue("customer_data" in config.toString())
        assertTrue("app_user" in config.toString())
        assertTrue("<redacted>" in config.toString())
        assertTrue("<redacted>" in connection.toString())
    }

    @Test
    fun parsesProductAndNumericVersionFromManagerVersionString() {
        assertEquals(
            DatabaseServerInfo(DatabaseProduct.MARIA_DB, MARIA_DB.Version(10, 4, 28)),
            DatabaseServerInfo.parse("10.4.28-MariaDB"),
        )
        assertEquals(
            DatabaseServerInfo(DatabaseProduct.MYSQL, MYSQL.Version(8, 0, 36)),
            DatabaseServerInfo.parse("8.0.36"),
        )
    }

    @Test
    fun rejectsProductMismatchAndCrossProductVersionComparison() {
        assertFailsWith<IllegalArgumentException> {
            DatabaseServerInfo(DatabaseProduct.MYSQL, MARIA_DB.Version(10, 4, 28))
        }
        assertFailsWith<IllegalArgumentException> {
            MYSQL.Version(8, 0, 0).compareTo(MARIA_DB.Version(10, 4, 28))
        }
        assertFailsWith<IllegalArgumentException> { MYSQL.Version(-1, 0, 0) }
    }
}
