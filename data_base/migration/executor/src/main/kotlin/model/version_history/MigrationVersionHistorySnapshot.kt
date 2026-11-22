package model.version_history

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import java.util.ArrayList
import java.util.Collections

/** Immutable read model for the three persisted System Migration tables. */
internal class MigrationVersionHistorySnapshot(
    tags: List<MigrationHistoryTag>,
    versions: List<MigrationHistoryVersion>,
    migrations: List<MigrationHistoryEntry>,
) {
    /** Tags are ordered by their persisted primary key, independently of query row order. */
    val tags: List<MigrationHistoryTag> = immutableCopy(tags.sortedBy(MigrationHistoryTag::id))

    /** Versions are ordered by their persisted primary key, independently of query row order. */
    val versions: List<MigrationHistoryVersion> = immutableCopy(versions.sortedBy(MigrationHistoryVersion::id))

    /** Migration history is ordered by id; timestamps remain optional data, not ordering keys. */
    val migrations: List<MigrationHistoryEntry> = immutableCopy(migrations.sortedBy(MigrationHistoryEntry::id))

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
    val status: Int,
    val message: String? = null,
    val retryCheckpoint: String? = null,
) {
    companion object {
        /** Persisted code for a Version whose migration and history writes completed. */
        const val STATUS_MIGRATE_COMPLETED = MigrationVersionState.STATUS_MIGRATE_COMPLETED
    }
}

internal data class MigrationHistoryEntry(
    val id: Int,
    val tagId: Int,
    val versionId: Int,
    val createdAt: String? = null,
)
