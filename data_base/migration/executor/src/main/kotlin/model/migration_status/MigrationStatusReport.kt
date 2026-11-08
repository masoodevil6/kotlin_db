package model.migration_status

import java.util.ArrayList
import java.util.Collections

/** Immutable analysis result, retaining configured order and persisted row identities. */
class MigrationStatusReport(
    versions: List<ConfiguredVersion>,
    unknownVersions: List<UnknownVersion>,
    unknownMigrations: List<UnknownMigration>,
    unknownTags: List<UnknownTag>,
    issues: List<Issue>,
) {
    val versions: List<ConfiguredVersion> = immutableCopy(versions)
    val unknownVersions: List<UnknownVersion> = immutableCopy(unknownVersions)
    val unknownMigrations: List<UnknownMigration> = immutableCopy(unknownMigrations)
    val unknownTags: List<UnknownTag> = immutableCopy(unknownTags)
    val issues: List<Issue> = immutableCopy(issues)

    /** A Version from MigrationConfiguration, in declaration order. */
    class ConfiguredVersion(
        val name: String,
        val status: MigrationVersionStatus,
        versionTagIds: List<Int>,
        versionIds: List<Int>,
        migrations: List<ConfiguredMigration>,
        issues: List<Issue>,
    ) {
        val versionTagIds: List<Int> = immutableCopy(versionTagIds)
        val versionIds: List<Int> = immutableCopy(versionIds)
        val migrations: List<ConfiguredMigration> = immutableCopy(migrations)
        val issues: List<Issue> = immutableCopy(issues)
    }

    /** A Migration declared inside a configured Version, in its flattened declaration order. */
    class ConfiguredMigration(
        val id: String,
        val status: MigrationItemStatus,
        tagIds: List<Int>,
        historyRecordIds: List<Int>,
        versionReferenceIds: List<Int>,
        issues: List<Issue>,
    ) {
        val tagIds: List<Int> = immutableCopy(tagIds)
        val historyRecordIds: List<Int> = immutableCopy(historyRecordIds)
        val versionReferenceIds: List<Int> = immutableCopy(versionReferenceIds)
        val issues: List<Issue> = immutableCopy(issues)
    }

    /** Persisted Version identity absent from the current MigrationConfiguration. */
    class UnknownVersion(
        val name: String?,
        val status: MigrationVersionStatus,
        tagIds: List<Int>,
        versionIds: List<Int>,
        historyRecordIds: List<Int>,
        issues: List<Issue>,
    ) {
        val tagIds: List<Int> = immutableCopy(tagIds)
        val versionIds: List<Int> = immutableCopy(versionIds)
        val historyRecordIds: List<Int> = immutableCopy(historyRecordIds)
        val issues: List<Issue> = immutableCopy(issues)
    }

    /** Persisted Migration identity absent from the current MigrationConfiguration. */
    class UnknownMigration(
        val id: String?,
        val status: MigrationItemStatus,
        tagIds: List<Int>,
        historyRecordIds: List<Int>,
        versionReferenceIds: List<Int>,
        issues: List<Issue>,
    ) {
        val tagIds: List<Int> = immutableCopy(tagIds)
        val historyRecordIds: List<Int> = immutableCopy(historyRecordIds)
        val versionReferenceIds: List<Int> = immutableCopy(versionReferenceIds)
        val issues: List<Issue> = immutableCopy(issues)
    }

    /** Persisted Tag whose type is not a supported persisted Migration Tag type. */
    class UnknownTag(
        val tagId: Int,
        val name: String,
        val typeCode: Int,
        versionIds: List<Int>,
        historyRecordIds: List<Int>,
        versionReferenceIds: List<Int>,
        issues: List<Issue>,
    ) {
        val versionIds: List<Int> = immutableCopy(versionIds)
        val historyRecordIds: List<Int> = immutableCopy(historyRecordIds)
        val versionReferenceIds: List<Int> = immutableCopy(versionReferenceIds)
        val issues: List<Issue> = immutableCopy(issues)
    }

    /** A separately reportable inconsistency; ID lists identify all affected persisted records. */
    class Issue(
        val code: Code,
        val message: String,
        affectedVersionNames: List<String> = emptyList(),
        affectedMigrationIds: List<String> = emptyList(),
        tagIds: List<Int> = emptyList(),
        versionIds: List<Int> = emptyList(),
        historyRecordIds: List<Int> = emptyList(),
        versionReferenceIds: List<Int> = emptyList(),
    ) {
        val affectedVersionNames: List<String> = immutableCopy(affectedVersionNames)
        val affectedMigrationIds: List<String> = immutableCopy(affectedMigrationIds)
        val tagIds: List<Int> = immutableCopy(tagIds)
        val versionIds: List<Int> = immutableCopy(versionIds)
        val historyRecordIds: List<Int> = immutableCopy(historyRecordIds)
        val versionReferenceIds: List<Int> = immutableCopy(versionReferenceIds)

        enum class Code {
            INVALID_PERSISTED_ID,
            BLANK_TAG_NAME,
            INVALID_VERSION_NAME,
            INVALID_MIGRATION_ID,
            INVALID_TAG_TYPE,
            DUPLICATE_TAG,
            INCONSISTENT_TAG_ID,
            DUPLICATE_VERSION_RECORD,
            INCONSISTENT_VERSION_RECORD,
            BROKEN_VERSION_REFERENCE,
            VERSION_REFERENCE_MISMATCH,
            BROKEN_VERSION_TAG_REFERENCE,
            INCOMPLETE_HISTORY_RECORD,
            DUPLICATE_HISTORY_RECORD,
            INCONSISTENT_HISTORY_RECORD,
            HISTORY_ATTACHED_TO_INVALID_TAG_TYPE,
            VERSION_ATTACHED_TO_INVALID_TAG_TYPE,
            WRONG_VERSION_ASSOCIATION,
            MISSING_EXPECTED_MIGRATION_HISTORY,
            INCOMPLETE_VERSION_HISTORY,
            UNKNOWN_PERSISTED_VERSION,
            UNKNOWN_PERSISTED_MIGRATION,
            UNKNOWN_PERSISTED_TAG,
            VERSION_ORDER_MISMATCH,
            UNEXPECTED_HISTORY_FOR_VERSION,
        }
    }
}

private fun <T> immutableCopy(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
