package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DatabaseRegistrationAttributeTest {
    private object Primary : DatabaseKey
    private object Analytics : DatabaseKey

    private val primaryLabel = DatabaseRegistrationAttribute.create<String>()
    private val secondLabel = DatabaseRegistrationAttribute.create<String>()

    @Test
    fun `registered value is available from its handle`() {
        val manager = DatabaseManager {
            database(Primary) {
                config = config("primary")
                attribute(primaryLabel, "main")
            }
        }

        try {
            val attributes = assertIs<DatabaseHandleAttributes>(manager[Primary])
            assertEquals("main", attributes.attribute(primaryLabel))
        } finally {
            manager.close()
        }
    }

    @Test
    fun `different keys with the same value type remain independent`() {
        val manager = DatabaseManager {
            database(Primary) {
                config = config("primary")
                attribute(primaryLabel, "primary-value")
                attribute(secondLabel, "second-value")
            }
        }

        try {
            val attributes = assertIs<DatabaseHandleAttributes>(manager[Primary])
            assertEquals("primary-value", attributes.attribute(primaryLabel))
            assertEquals("second-value", attributes.attribute(secondLabel))
        } finally {
            manager.close()
        }
    }

    @Test
    fun `missing attribute returns null`() {
        val manager = DatabaseManager {
            database(Primary) { config = config("primary") }
        }

        try {
            val attributes = assertIs<DatabaseHandleAttributes>(manager[Primary])
            assertNull(attributes.attribute(primaryLabel))
        } finally {
            manager.close()
        }
    }

    @Test
    fun `attribute values are isolated between registrations`() {
        val manager = DatabaseManager {
            database(Primary) {
                config = config("primary")
                attribute(primaryLabel, "main")
            }
            database(Analytics) {
                config = config("analytics")
                attribute(primaryLabel, "events")
            }
        }

        try {
            val primary = assertIs<DatabaseHandleAttributes>(manager[Primary])
            val analytics = assertIs<DatabaseHandleAttributes>(manager[Analytics])
            assertEquals("main", primary.attribute(primaryLabel))
            assertEquals("events", analytics.attribute(primaryLabel))
        } finally {
            manager.close()
        }
    }

    @Test
    fun `registration owns a snapshot independent of later builder changes`() {
        val builder = DatabaseRegistrationBuilder().apply {
            config = config("primary")
            attribute(primaryLabel, "at-registration")
        }
        val registration = builder.build(Primary)

        builder.attribute(primaryLabel, "after-registration")

        assertEquals("at-registration", registration.attributes[primaryLabel])
        assertEquals("at-registration", primaryLabel.checkedValue(registration.attributes.getValue(primaryLabel)))
        @Suppress("UNCHECKED_CAST")
        val mutableView = registration.attributes as MutableMap<DatabaseRegistrationAttribute<*>, Any>
        assertFailsWith<UnsupportedOperationException> {
            mutableView[primaryLabel] = "attempted-mutation"
        }
    }

    @Test
    fun `custom DatabaseHandleApi remains compatible without attribute capability`() {
        val custom = CustomHandle()

        assertFalse((custom as Any) is DatabaseHandleAttributes)
        assertEquals(Primary, custom.key)
    }

    private class CustomHandle : DatabaseHandleApi {
        override val key: DatabaseKey = Primary

        override fun <T> connection(
            block: (gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor) ->
                gog.my_project.data_base_manager.execute.tools.ExecuteResult<T>,
        ): gog.my_project.data_base_manager.execute.tools.ExecuteResult<T> =
            throw UnsupportedOperationException()

        override fun <T> transaction(
            block: (gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor) ->
                gog.my_project.data_base_manager.execute.tools.ExecuteResult<T>,
        ): gog.my_project.data_base_manager.execute.tools.ExecuteResult<T> =
            throw UnsupportedOperationException()
    }

    private fun config(name: String) = DatabaseConfig(
        connection = DatabaseConnectionConfig(
            dbDomain = "jdbc:mysql://127.0.0.1",
            dbPort = 3306,
            dbName = name,
            dbUserName = "user",
            dbPassword = "password",
        ),
    )
}
