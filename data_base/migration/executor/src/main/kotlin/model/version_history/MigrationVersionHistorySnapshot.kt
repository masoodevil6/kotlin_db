package model.version_history

import java.util.ArrayList
import java.util.Collections

/** Immutable read model for the three persisted System Migration tables. */
internal class MigrationVersionHistorySnapshot(
    tags: List<MigrationHistoryTag>,
    versions: List<MigrationHistoryVersion>,
    migrations: List<MigrationHistoryEntry>,
) {
    val tags: List<MigrationHistoryTag> = immutableCopy(tags)
    val versions: List<MigrationHistoryVersion> = immutableCopy(versions)
    val migrations: List<MigrationHistoryEntry> = immutableCopy(migrations)

    private fun <T> immutableCopy(values: List<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}

internal data class MigrationHistoryTag(
    val id: Int,
    val name: String,
    val type: Int,
)

internal data class MigrationHistoryVersion(
    val id: Int,
    val tagId: Int,
)

internal data class MigrationHistoryEntry(
    val id: Int,
    val tagId: Int,
    val versionId: Int,
)
