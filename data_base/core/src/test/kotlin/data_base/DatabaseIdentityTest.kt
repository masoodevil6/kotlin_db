package gog.my_project.data_base_operation.config.data_base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseIdentityTest {
    @Test
    fun `equivalent host spelling and effective default port produce the same identity`() {
        val implicitPort = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://LOCALHOST.", null, "customers", "u1", "p1"),
        )
        val explicitPort = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://localhost", 3306, "customers", "u2", "p2"),
        )

        assertEquals(DatabaseIdentity.from(implicitPort), DatabaseIdentity.from(explicitPort))
        assertTrue(requireNotNull(DatabaseIdentity.from(implicitPort)).value.startsWith("db-target-v1:"))
    }

    @Test
    fun `different configured endpoint or catalog produces a different identity`() {
        val baseline = identity(host = "db-a.internal", port = 3306, database = "catalog_a")

        assertNotEquals(baseline, identity(host = "db-b.internal", port = 3306, database = "catalog_a"))
        assertNotEquals(baseline, identity(host = "db-a.internal", port = 3307, database = "catalog_a"))
        assertNotEquals(baseline, identity(host = "db-a.internal", port = 3306, database = "catalog_b"))
    }

    @Test
    fun `lowercase catalog is accepted`() {
        assertNotNull(identity(host = "db.internal", port = 3306, database = "appdb"))
    }

    @Test
    fun `catalogs with uppercase ascii letters are rejected`() {
        assertNull(identity(host = "db.internal", port = 3306, database = "AppDb"))
        assertNull(identity(host = "db.internal", port = 3306, database = "APPDB"))
    }

    @Test
    fun `numeric and non-letter catalog characters remain accepted`() {
        assertNotNull(identity(host = "db.internal", port = 3306, database = "12345"))
        assertNotNull(identity(host = "db.internal", port = 3306, database = "app_db-01.$"))
    }

    @Test
    fun `empty and invalid catalogs are rejected`() {
        assertNull(identity(host = "db.internal", port = 3306, database = ""))
        assertNull(identity(host = "db.internal", port = 3306, database = "app/db"))
        assertNull(identity(host = "db.internal", port = 3306, database = "app db"))
    }

    @Test
    fun `credentials do not affect database identity`() {
        val first = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://db.internal", 3306, "catalog", "alice", "first-secret"),
        )
        val second = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://db.internal", 3306, "catalog", "bob", "second-secret"),
        )

        val firstIdentity = assertNotNull(DatabaseIdentity.from(first))
        val secondIdentity = assertNotNull(DatabaseIdentity.from(second))
        assertEquals(firstIdentity, secondIdentity)
        assertTrue("first-secret" !in firstIdentity.toString())
        assertTrue("alice" !in firstIdentity.toString())
    }

    @Test
    fun `unsupported or ambiguous destination settings fail closed`() {
        assertNull(DatabaseIdentity.from(config(domain = "jdbc:other://host")))
        assertNull(DatabaseIdentity.from(config(domain = "jdbc:mysql://host?route=replica")))
        assertNull(DatabaseIdentity.from(config(domain = "jdbc:mysql://host-a,host-b")))
        assertNull(DatabaseIdentity.from(config(domain = "jdbc:mysql://host", database = "")))
        assertNull(DatabaseIdentity.from(config(domain = "jdbc:mysql://host", port = 0)))
    }

    @Test
    fun `system lock accepts only a non blank logical identifier`() {
        assertEquals("migration", SystemLock("migration").id)
        assertEquals("migration", SystemLock.MIGRATION.id)
        assertEquals("schema-update", SystemLock.SCHEMA_UPDATE.id)
        assertEquals("database-initialization", SystemLock.DATABASE_INITIALIZATION.id)
        assertEquals(
            3,
            setOf(SystemLock.MIGRATION, SystemLock.SCHEMA_UPDATE, SystemLock.DATABASE_INITIALIZATION).size,
        )
        assertFailsWith<IllegalArgumentException> { SystemLock("  ") }
    }

    private fun identity(host: String, port: Int?, database: String): DatabaseIdentity? =
        DatabaseIdentity.from(config("jdbc:mysql://$host", port, database))

    private fun config(
        domain: String,
        port: Int? = 3306,
        database: String = "catalog",
    ) = DatabaseConfig(
        connection = DatabaseConnectionConfig(domain, port, database, "user", "password"),
    )
}
