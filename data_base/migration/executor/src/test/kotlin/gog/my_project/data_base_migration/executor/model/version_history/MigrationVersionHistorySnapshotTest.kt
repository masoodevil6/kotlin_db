package gog.my_project.data_base_migration.executor.model.version_history

import model.version_history.MigrationHistoryEntry
import model.version_history.MigrationHistoryTag
import model.version_history.MigrationHistoryVersion
import model.version_history.MigrationVersionHistorySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MigrationVersionHistorySnapshotTest {
    @Test
    fun `orders raw collections by persisted id and preserves nullable timestamps`() {
        val snapshot = MigrationVersionHistorySnapshot(
            tags = listOf(
                MigrationHistoryTag(8, "tag-8", 1),
                MigrationHistoryTag(2, "tag-2", 0),
            ),
            versions = listOf(
                MigrationHistoryVersion(30, 8),
                MigrationHistoryVersion(4, 2),
            ),
            migrations = listOf(
                MigrationHistoryEntry(30, 8, 30, "2026-01-01T00:00:00Z"),
                MigrationHistoryEntry(7, 2, 4, null),
                MigrationHistoryEntry(18, 2, 4, "2026-01-01T00:00:00Z"),
            ),
        )

        assertEquals(listOf(2, 8), snapshot.tags.map(MigrationHistoryTag::id))
        assertEquals(listOf(4, 30), snapshot.versions.map(MigrationHistoryVersion::id))
        assertEquals(listOf(7, 18, 30), snapshot.migrations.map(MigrationHistoryEntry::id))
        assertNull(snapshot.migrations.first().createdAt)
        assertEquals(
            listOf("2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"),
            snapshot.migrations.drop(1).map(MigrationHistoryEntry::createdAt),
        )
    }

    @Test
    fun `defensively copies all input lists and exposes unmodifiable lists`() {
        val tag = MigrationHistoryTag(1, "tag", 0)
        val version = MigrationHistoryVersion(1, 1)
        val entry = MigrationHistoryEntry(1, 1, 1, null)
        val tags = mutableListOf(tag)
        val versions = mutableListOf(version)
        val migrations = mutableListOf(entry)
        val snapshot = MigrationVersionHistorySnapshot(tags, versions, migrations)

        tags.clear()
        versions.clear()
        migrations.clear()

        assertEquals(listOf(tag), snapshot.tags)
        assertEquals(listOf(version), snapshot.versions)
        assertEquals(listOf(entry), snapshot.migrations)
        assertFailsWith<UnsupportedOperationException> {
            (snapshot.tags as MutableList).add(MigrationHistoryTag(2, "other", 0))
        }
        assertFailsWith<UnsupportedOperationException> {
            (snapshot.versions as MutableList).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (snapshot.migrations as MutableList).clear()
        }
    }

    @Test
    fun `retains duplicate evidence and sparse ids`() {
        val duplicate = MigrationHistoryEntry(14, 5, 9, null)
        val snapshot = MigrationVersionHistorySnapshot(
            tags = listOf(MigrationHistoryTag(5, "item", 1)),
            versions = listOf(MigrationHistoryVersion(9, 2)),
            migrations = listOf(MigrationHistoryEntry(80, 5, 9), duplicate, duplicate),
        )

        assertEquals(listOf(14, 14, 80), snapshot.migrations.map(MigrationHistoryEntry::id))
        assertEquals(2, snapshot.migrations.count { it == duplicate })
    }
}
