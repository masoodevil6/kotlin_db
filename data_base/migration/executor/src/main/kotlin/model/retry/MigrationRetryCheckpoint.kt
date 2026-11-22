package model.retry

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.charset.StandardCharsets

/** Persisted execution cursor owned and interpreted only by Migration Executor. */
internal data class MigrationRetryCheckpoint(
    val formatVersion: Int,
    val revision: Long,
    val originalStatus: Int,
    val originalMessage: String?,
    val cursorMigrationId: String?,
    val phase: MigrationRetryPhase,
)

internal enum class MigrationRetryPhase {
    EXECUTION_PENDING,
    EXECUTION_IN_FLIGHT,
    RECORD_PENDING,
    PAUSED_NEXT,
    FINALIZATION_PENDING,
}

/** Strict, deterministic codec. It rejects unknown structure and never truncates persisted data. */
internal object MigrationRetryCheckpointCodec {
    const val FORMAT_VERSION = 1
    const val MAX_UTF8_BYTES = 16 * 1024

    private val requiredKeys = linkedSetOf(
        "formatVersion",
        "revision",
        "originalStatus",
        "originalMessage",
        "cursorMigrationId",
        "phase",
    )

    fun encode(checkpoint: MigrationRetryCheckpoint): String {
        validate(checkpoint)
        val json: JsonElement = buildJsonObject {
            put("formatVersion", JsonPrimitive(checkpoint.formatVersion))
            put("revision", JsonPrimitive(checkpoint.revision))
            put("originalStatus", JsonPrimitive(checkpoint.originalStatus))
            put("originalMessage", checkpoint.originalMessage?.let(::JsonPrimitive) ?: JsonNull)
            put("cursorMigrationId", checkpoint.cursorMigrationId?.let(::JsonPrimitive) ?: JsonNull)
            put("phase", JsonPrimitive(checkpoint.phase.name))
        }
        return Json.encodeToString(JsonElement.serializer(), json).also(::checkSize)
    }

    fun decode(value: String): MigrationRetryCheckpoint {
        checkSize(value)
        val fields = StrictObjectParser(value).parse()
        require(fields.keys == requiredKeys) {
            "Retry Checkpoint fields must be exactly ${requiredKeys.joinToString()}"
        }
        val checkpoint = MigrationRetryCheckpoint(
            formatVersion = fields.getValue("formatVersion").intValue("formatVersion"),
            revision = fields.getValue("revision").longValue("revision"),
            originalStatus = fields.getValue("originalStatus").intValue("originalStatus"),
            originalMessage = fields.getValue("originalMessage").nullableStringValue("originalMessage"),
            cursorMigrationId = fields.getValue("cursorMigrationId").nullableStringValue("cursorMigrationId"),
            phase = fields.getValue("phase").stringValue("phase").let { phaseName ->
                MigrationRetryPhase.entries.singleOrNull { it.name == phaseName }
                    ?: throw IllegalArgumentException("Unknown Retry Checkpoint phase '$phaseName'")
            },
        )
        validate(checkpoint)
        return checkpoint
    }

    fun nextRevision(checkpoint: MigrationRetryCheckpoint): Long {
        require(checkpoint.revision < Long.MAX_VALUE) { "Retry Checkpoint revision overflow" }
        return checkpoint.revision + 1
    }

    private fun validate(checkpoint: MigrationRetryCheckpoint) {
        require(checkpoint.formatVersion == FORMAT_VERSION) { "Unsupported Retry Checkpoint format" }
        require(checkpoint.revision >= 0) { "Retry Checkpoint revision must not be negative" }
        require(
            checkpoint.originalStatus == MigrationVersionState.STATUS_MIGRATE_STARTED ||
                checkpoint.originalStatus == MigrationVersionState.STATUS_ROLLBACK_STARTED,
        ) { "Retry Checkpoint originalStatus must identify UP or DOWN" }

        if (checkpoint.phase == MigrationRetryPhase.FINALIZATION_PENDING) {
            require(checkpoint.cursorMigrationId == null) {
                "FINALIZATION_PENDING must not have a Migration cursor"
            }
        } else {
            val migrationId = requireNotNull(checkpoint.cursorMigrationId) {
                "${checkpoint.phase} requires a Migration cursor"
            }
            require(MigrationIdentity.fromValue(migrationId).value == migrationId) {
                "Retry Checkpoint Migration cursor is invalid"
            }
        }
    }

    private fun checkSize(value: String) {
        require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_UTF8_BYTES) {
            "Retry Checkpoint exceeds $MAX_UTF8_BYTES UTF-8 bytes"
        }
    }

    /** Strict scalar token returned by [StrictObjectParser], also reused by Migration diagnostics. */
    internal data class RawValue(val source: String) {
        fun intValue(name: String): Int {
            require(INTEGER.matches(source)) { "Retry Checkpoint '$name' must be an integer" }
            return source.toIntOrNull() ?: throw IllegalArgumentException("Retry Checkpoint '$name' is out of range")
        }

        fun longValue(name: String): Long {
            require(INTEGER.matches(source)) { "Retry Checkpoint '$name' must be an integer" }
            return source.toLongOrNull() ?: throw IllegalArgumentException("Retry Checkpoint '$name' is out of range")
        }

        fun stringValue(name: String): String = parseString(name)

        fun nullableStringValue(name: String): String? =
            if (source == "null") null else parseString(name)

        private fun parseString(name: String): String {
            val element = try {
                Json.parseToJsonElement(source)
            } catch (failure: Exception) {
                throw IllegalArgumentException("Retry Checkpoint '$name' is not valid JSON string data", failure)
            }
            require(element is JsonPrimitive && element.isString) {
                "Retry Checkpoint '$name' must be a string"
            }
            return element.content
        }

        private companion object {
            val INTEGER = Regex("-?(0|[1-9][0-9]*)")
        }
    }

    /**
     * Parses the outer object before kotlinx.serialization's object model so duplicate JSON keys
     * cannot be silently collapsed. Values are intentionally limited to scalar JSON tokens.
     */
    /**
     * Parses a flat JSON object without collapsing duplicate keys. Kept internal so the Retry
     * Diagnostic validator can use the same proven strict parser rather than a permissive JsonObject.
     */
    internal class StrictObjectParser(private val source: String) {
        private var index = 0

        fun parse(): LinkedHashMap<String, RawValue> {
            skipWhitespace()
            expect('{')
            skipWhitespace()
            val values = LinkedHashMap<String, RawValue>()
            if (consume('}')) {
                finish()
                return values
            }
            while (true) {
                skipWhitespace()
                val key = readJsonString()
                require(key !in values) { "Duplicate Retry Checkpoint field '$key'" }
                skipWhitespace()
                expect(':')
                skipWhitespace()
                val raw = readScalar()
                values[key] = RawValue(raw)
                skipWhitespace()
                if (consume('}')) break
                expect(',')
            }
            finish()
            return values
        }

        private fun readScalar(): String {
            val start = index
            if (peek() == '"') {
                readJsonString()
                return source.substring(start, index)
            }
            while (index < source.length && source[index] != ',' && source[index] != '}') {
                require(source[index] != '{' && source[index] != '[') {
                    "Retry Checkpoint values must be scalar"
                }
                index++
            }
            return source.substring(start, index).trim()
        }

        private fun readJsonString(): String {
            val start = index
            expect('"')
            var escaped = false
            while (index < source.length) {
                val character = source[index++]
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    val raw = source.substring(start, index)
                    val parsed = try {
                        Json.parseToJsonElement(raw)
                    } catch (failure: Exception) {
                        throw IllegalArgumentException("Malformed Retry Checkpoint JSON string", failure)
                    }
                    require(parsed is JsonPrimitive && parsed.isString) { "Expected JSON string" }
                    return parsed.content
                }
            }
            throw IllegalArgumentException("Unterminated Retry Checkpoint JSON string")
        }

        private fun finish() {
            skipWhitespace()
            require(index == source.length) { "Trailing data after Retry Checkpoint JSON" }
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index] in WHITESPACE) index++
        }

        private fun peek(): Char? = source.getOrNull(index)

        private fun consume(expected: Char): Boolean {
            if (peek() != expected) return false
            index++
            return true
        }

        private fun expect(expected: Char) {
            require(consume(expected)) { "Expected '$expected' in Retry Checkpoint JSON" }
        }

        private companion object {
            val WHITESPACE = charArrayOf(' ', '\t', '\r', '\n')
        }
    }
}
