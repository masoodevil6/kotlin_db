package gog.my_project.data_base_operation.migration.failure.spi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationFailureDiagnosticJsonTest {
    @Test
    fun `stored diagnostic is versioned JSON with the required field types`() {
        val diagnostic = diagnostic(
            sqlState = "42S21",
            errorCode = 1060,
            message = "Duplicate column name display_name",
            migrationId = "create_manager_customers",
        )

        val stored = diagnostic.toStorageMessage(versionId = 8)
        val json = Json.parseToJsonElement(stored).jsonObject

        assertEquals(1, json.getValue("schemaVersion").jsonPrimitive.int)
        assertFalse(json.getValue("schemaVersion").jsonPrimitive.isString)
        assertEquals("UP_EXECUTION", json.getValue("stage").jsonPrimitive.content)
        assertEquals(8, json.getValue("versionId").jsonPrimitive.int)
        assertEquals("create_manager_customers", json.getValue("migrationId").jsonPrimitive.content)
        assertEquals("java.sql.SQLException", json.getValue("exception").jsonPrimitive.content)
        assertEquals("42S21", json.getValue("sqlState").jsonPrimitive.content)
        assertEquals(1060, json.getValue("errorCode").jsonPrimitive.int)
        assertEquals("Duplicate column name display_name", json.getValue("message").jsonPrimitive.content)
        assertEquals(
            setOf("schemaVersion", "stage", "versionId", "migrationId", "exception", "sqlState", "errorCode", "message"),
            json.keys,
        )
    }

    @Test
    fun `nullable fields are explicit and no Migration ID is fabricated`() {
        val diagnostic = diagnostic(sqlState = null, errorCode = null, message = null, migrationId = null)
        val json = Json.parseToJsonElement(diagnostic.toStorageMessage(versionId = 19)).jsonObject

        assertEquals(JsonNull, json.getValue("migrationId"))
        assertEquals(JsonNull, json.getValue("sqlState"))
        assertEquals(JsonNull, json.getValue("errorCode"))
        assertEquals(19, json.getValue("versionId").jsonPrimitive.int)
        assertEquals("", json.getValue("message").jsonPrimitive.content)
        assertNull(diagnostic.migrationId)
    }

    @Test
    fun `JSON safely encodes quotes slashes Unicode and sanitized control characters`() {
        val diagnostic = diagnostic(
            sqlState = null,
            errorCode = null,
            message = "path=C:\\db/table; unmatched quote \" slash / control\u0001 newline\n emoji 🧪",
            migrationId = null,
        )
        val stored = diagnostic.toStorageMessage(versionId = 3)
        val parsedMessage = Json.parseToJsonElement(stored).jsonObject.getValue("message").jsonPrimitive.content

        assertEquals(diagnostic.message, parsedMessage)
        assertTrue(stored.contains("\\\""), "JSON encoder must escape a quote in the message")
        assertTrue(stored.contains("\\\\"), "JSON encoder must escape a backslash in the message")
        assertTrue(parsedMessage.contains("/"), "Slash content must round-trip")
        assertTrue(parsedMessage.contains("🧪"), "Unicode content must round-trip")
        assertFalse(stored.any { Character.isISOControl(it) }, "Stored JSON must not contain raw control characters")
        assertFalse(parsedMessage.contains('\n'), "Diagnostic sanitation must prevent newline injection")
    }

    @Test
    fun `redaction remains in effect before JSON serialization`() {
        val stored = diagnostic(
            sqlState = null,
            errorCode = null,
            message = "password=hunter2 jdbc:mysql://db.internal/app?user=alice&password=secret",
            migrationId = null,
        ).toStorageMessage(versionId = 4)
        val parsed = Json.parseToJsonElement(stored).jsonObject.getValue("message").jsonPrimitive.content

        assertFalse(parsed.contains("hunter2"))
        assertFalse(parsed.contains("db.internal"))
        assertFalse(parsed.contains("alice"))
        assertTrue(parsed.contains("[REDACTED]"))
        assertTrue(parsed.contains("[REDACTED_URL]"))
    }

    @Test
    fun `full JSON respects code point limit and truncation keeps surrogate pairs intact`() {
        val diagnostic = diagnostic(
            sqlState = "42S21",
            errorCode = 1060,
            message = "😀x".repeat(3000),
            migrationId = "create_manager_customers",
        )
        val stored = diagnostic.toStorageMessage(versionId = 2)
        val parsedMessage = Json.parseToJsonElement(stored).jsonObject.getValue("message").jsonPrimitive.content

        assertTrue(stored.codePointCount(0, stored.length) <= MigrationFailureDiagnostic.MAX_STORAGE_MESSAGE_LENGTH)
        assertWellFormedSurrogates(stored)
        assertWellFormedSurrogates(parsedMessage)
        assertTrue(parsedMessage.isNotEmpty())
    }

    @Test
    fun `contextless formatter is JSON but persisted formatter uses command Version ID`() {
        val diagnostic = diagnostic(sqlState = null, errorCode = null, message = "failure", migrationId = null)

        val contextless = Json.parseToJsonElement(diagnostic.toStorageMessage()).jsonObject
        val persisted = Json.parseToJsonElement(diagnostic.toStorageMessage(versionId = 24)).jsonObject

        assertEquals(JsonNull, contextless.getValue("versionId"))
        assertEquals(24, persisted.getValue("versionId").jsonPrimitive.int)
    }

    private fun diagnostic(
        sqlState: String?,
        errorCode: Int?,
        message: String?,
        migrationId: String?,
    ) = MigrationFailureDiagnostic(
        stage = MigrationFailureDiagnosticStage.UP_EXECUTION,
        exceptionClassName = "java.sql.SQLException",
        sqlState = sqlState,
        vendorErrorCode = errorCode,
        message = message,
        migrationId = migrationId,
    )

    private fun assertWellFormedSurrogates(value: String) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                Character.isHighSurrogate(char) -> {
                    assertTrue(index + 1 < value.length && Character.isLowSurrogate(value[index + 1]))
                    index += 2
                }
                Character.isLowSurrogate(char) -> error("Found an unpaired low surrogate")
                else -> index++
            }
        }
    }
}
