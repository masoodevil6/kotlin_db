package gog.my_project.data_base_operation.migration.failure.spi

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** The four failure categories that may be persisted by the restricted Migration capability. */
public enum class MigrationFailureDiagnosticStage {
    UP_EXECUTION,
    UP_RECORDING,
    DOWN_EXECUTION,
    DOWN_RECORDING,
}

/**
 * Immutable, JDBC- and Operation-neutral diagnostic data. Free-form exception text is sanitized
 * before it is exposed through [message] or formatted for persistence.
 */
public class MigrationFailureDiagnostic(
    public val stage: MigrationFailureDiagnosticStage,
    exceptionClassName: String,
    sqlState: String?,
    public val vendorErrorCode: Int?,
    message: String?,
    migrationId: String?,
) {
    /** Preserves the original public JVM constructor for existing callers. */
    public constructor(
        stage: MigrationFailureDiagnosticStage,
        exceptionClassName: String,
        sqlState: String?,
        vendorErrorCode: Int?,
        message: String?,
    ) : this(stage, exceptionClassName, sqlState, vendorErrorCode, message, migrationId = null)

    public val exceptionClassName: String = normalizeExceptionClass(exceptionClassName)
    public val sqlState: String? = normalizeSqlState(sqlState)
    public val message: String? = sanitizeMessage(message)
    public val migrationId: String? = normalizeMigrationId(migrationId)

    /**
     * Formats a versioned JSON diagnostic without command context. This compatibility overload
     * records `versionId` as JSON null; persisted Capability writes use the command-aware overload.
     */
    public fun toStorageMessage(): String = serialize(versionId = null)

    /** Formats a diagnostic whose Version ID comes from the status command. */
    public fun toStorageMessage(versionId: Int): String = serialize(versionId)

    private fun serialize(versionId: Int?): String {
        val text = message.orEmpty()
        val emptyMessage = encodeJson(versionId, "")
        checkCodePointLimit(emptyMessage)

        val messageCodePoints = text.codePointCount(0, text.length)
        var lower = 0
        var upper = messageCodePoints
        var best = emptyMessage

        // JSON escaping can expand a character (for example, a control character). Search by
        // Unicode code point so truncation never splits a surrogate pair.
        while (lower <= upper) {
            val candidateLength = lower + (upper - lower) / 2
            val endIndex = text.offsetByCodePoints(0, candidateLength)
            val candidate = encodeJson(versionId, text.substring(0, endIndex))
            if (candidate.codePointCount(0, candidate.length) <= MAX_STORAGE_MESSAGE_LENGTH) {
                best = candidate
                lower = candidateLength + 1
            } else {
                upper = candidateLength - 1
            }
        }
        return best
    }

    private fun encodeJson(versionId: Int?, storedMessage: String): String {
        val element: JsonElement = buildJsonObject {
            put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
            put("stage", JsonPrimitive(stage.name))
            put("versionId", versionId?.let(::JsonPrimitive) ?: JsonNull)
            put("migrationId", migrationId?.let(::JsonPrimitive) ?: JsonNull)
            put("exception", JsonPrimitive(exceptionClassName))
            put("sqlState", sqlState?.let(::JsonPrimitive) ?: JsonNull)
            put("errorCode", vendorErrorCode?.let(::JsonPrimitive) ?: JsonNull)
            put("message", JsonPrimitive(storedMessage))
        }
        return Json.encodeToString(JsonElement.serializer(), element)
    }

    private fun checkCodePointLimit(value: String) {
        check(value.codePointCount(0, value.length) <= MAX_STORAGE_MESSAGE_LENGTH) {
            "Fixed Migration failure diagnostic fields exceed the storage limit"
        }
    }

    public companion object {
        public const val SCHEMA_VERSION: Int = 1
        public const val MAX_STORAGE_MESSAGE_LENGTH: Int = 2048

        private val exceptionClassPattern = Regex("[A-Za-z_$][A-Za-z0-9_.$]*")
        private val sqlStatePattern = Regex("[A-Za-z0-9_-]{1,16}")
        // Mirrors MigrationIdentity's current validated value grammar while keeping the SPI
        // independent of Migration Core. Migration executor tests keep the grammars in parity.
        private const val MAX_MIGRATION_ID_LENGTH = 255
        private val migrationIdentityPattern = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")
        private val sqlStatementPattern = Regex(
            "(?is)\\b(?:SELECT\\s+.+?\\s+FROM\\b|(?:INSERT|REPLACE)\\s+INTO\\b|" +
                "UPDATE\\s+[^\\s]+\\s+SET\\b|DELETE\\s+FROM\\b|" +
                "(?:CREATE|ALTER|DROP)\\s+(?:(?:OR\\s+REPLACE|TEMPORARY)\\s+)*" +
                "(?:TABLE|DATABASE|SCHEMA|INDEX|VIEW|TRIGGER|PROCEDURE|FUNCTION|EVENT|SEQUENCE|USER|ROLE)\\b|" +
                "TRUNCATE\\s+(?:TABLE\\s+)?|RENAME\\s+TABLE\\b|CALL\\s+[^\\s(]+)",
        )
        private val urlPattern = Regex("(?i)\\b(?:jdbc:[^\\s,;]+|[a-z][a-z0-9+.-]*://[^\\s,;]+)")
        private val bearerPattern = Regex("(?i)\\bBearer\\s+[^\\s,;]+")
        private val secretAssignmentPattern = Regex(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|authorization|username|user)" +
                "[\\\"']?\\s*[:=]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}&]+)",
        )
        private val quotedValuePattern = Regex("\\\"[^\\\"\\r\\n]*\\\"|'[^'\\r\\n]*'")
        private val whitespacePattern = Regex("\\s+")

        private fun normalizeExceptionClass(value: String): String =
            value.takeIf { exceptionClassPattern.matches(it) }
                ?.let { truncateCodePoints(it, 256) }
                ?: "unknown"

        private fun normalizeSqlState(value: String?): String? =
            value?.takeIf { sqlStatePattern.matches(it) }

        private fun normalizeMigrationId(value: String?): String? =
            value?.takeIf { it.length <= MAX_MIGRATION_ID_LENGTH && migrationIdentityPattern.matches(it) }

        private fun sanitizeMessage(value: String?): String? {
            if (value.isNullOrBlank()) return null

            var sanitized = if (sqlStatementPattern.containsMatchIn(value)) {
                "[SQL text redacted]"
            } else {
                value
            }
            sanitized = urlPattern.replace(sanitized, "[REDACTED_URL]")
            sanitized = bearerPattern.replace(sanitized, "Bearer [REDACTED]")
            sanitized = secretAssignmentPattern.replace(sanitized, "$1=[REDACTED]")
            sanitized = quotedValuePattern.replace(sanitized, "[REDACTED]")
            sanitized = replaceControlsAndMalformedSurrogates(sanitized)
            sanitized = whitespacePattern.replace(sanitized, " ").trim()

            return sanitized.takeIf { it.isNotEmpty() }
                ?.let { truncateCodePoints(it, MAX_STORAGE_MESSAGE_LENGTH) }
        }

        private fun replaceControlsAndMalformedSurrogates(value: String): String = buildString(value.length) {
            var index = 0
            while (index < value.length) {
                val character = value[index]
                when {
                    Character.isHighSurrogate(character) -> {
                        if (index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                            append(character)
                            append(value[index + 1])
                            index += 2
                        } else {
                            append('\uFFFD')
                            index++
                        }
                    }
                    Character.isLowSurrogate(character) -> {
                        append('\uFFFD')
                        index++
                    }
                    Character.isISOControl(character) -> {
                        append(' ')
                        index++
                    }
                    else -> {
                        append(character)
                        index++
                    }
                }
            }
        }

        private fun truncateCodePoints(value: String, maxCodePoints: Int): String {
            val codePointCount = value.codePointCount(0, value.length)
            if (codePointCount <= maxCodePoints) return value
            val endIndex = value.offsetByCodePoints(0, maxCodePoints)
            return value.substring(0, endIndex)
        }
    }
}
