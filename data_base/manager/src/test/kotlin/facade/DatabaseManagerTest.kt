package gog.my_project.data_base_manager.facade

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DatabaseConnectionConfig
import gog.my_project.data_base.core.data_base.DatabasePlatformConfig
import gog.my_project.data_base.core.data_base.DatabasePoolConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DatabaseManagerTest {
    @Test
    fun registersAndSelectsAContextWithoutOpeningAConnection() {
        val manager = DatabaseManager()
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://localhost", 3306, "sample", "user", "secret"),
            pool = DatabasePoolConfig(maxPoolSize = 3),
            platform = DatabasePlatformConfig(dialect = DialectQuery.MARIA_DB),
        )

        val registered = manager.register("analytics", config)

        assertSame(registered, manager.select("analytics"))
        assertEquals(setOf("analytics"), manager.registeredNames())
        assertEquals(config.connection, registered.connectionConfig)
        assertEquals(config.pool, registered.poolConfig)
        assertEquals(config.platform, registered.platformConfig)
    }

    @Test
    fun duplicateNamesAreRejectedWithoutReplacingTheRegisteredContext() {
        val manager = DatabaseManager()
        val first = manager.register("primary", config("first"))

        assertFailsWith<IllegalStateException> { manager.register("primary", config("second")) }
        assertSame(first, manager.select("primary"))
    }

    private fun config(name: String) = DatabaseConfig(
        connection = DatabaseConnectionConfig("jdbc:mysql://localhost", 3306, name, "user", "secret"),
    )
}
