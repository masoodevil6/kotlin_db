package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import model.version_history.MigrationHistoryEntry
import model.version_history.MigrationHistoryTag
import model.version_history.MigrationHistoryVersion
import model.version_history.MigrationVersionHistorySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MigrationDownRecordingReconcilerTest {
    private val reconciler = MigrationDownRecordingReconciler()
    private val migrationId = MigrationIdentity.fromValue("create_orders")

    @Test
    fun `deleted target history with an unreferenced tag schedules only tag deletion`() {
        val snapshot = snapshot(
            tags = listOf(versionTag(), itemTag()),
            versions = listOf(targetVersion()),
        )

        assertEquals(
            MigrationDownRecordingDecision.DeleteUnreferencedItemTag(ITEM_TAG_ID),
            reconciler.assess(snapshot, TARGET_VERSION_ID, migrationId),
        )
    }

    @Test
    fun `deleted target history and tag is completed without another write`() {
        val snapshot = snapshot(
            tags = listOf(versionTag()),
            versions = listOf(targetVersion()),
        )

        assertEquals(
            MigrationDownRecordingDecision.Complete(retainedTagId = null),
            reconciler.assess(snapshot, TARGET_VERSION_ID, migrationId),
        )
    }

    @Test
    fun `missing item tag with contradictory or dangling evidence fails closed`() {
        val wrongTypeSnapshot = snapshot(
            tags = listOf(versionTag(), MigrationHistoryTag(ITEM_TAG_ID, migrationId.value, MigrationTagType.VERSION.code)),
            versions = listOf(targetVersion()),
        )
        assertIs<MigrationDownRecordingDecision.Inconsistent>(
            reconciler.assess(wrongTypeSnapshot, TARGET_VERSION_ID, migrationId),
        )

        val danglingHistorySnapshot = snapshot(
            tags = listOf(versionTag()),
            versions = listOf(targetVersion()),
            migrations = listOf(MigrationHistoryEntry(TARGET_HISTORY_ID, ITEM_TAG_ID, TARGET_VERSION_ID)),
        )
        assertIs<MigrationDownRecordingDecision.Inconsistent>(
            reconciler.assess(danglingHistorySnapshot, TARGET_VERSION_ID, migrationId),
        )
    }

    @Test
    fun `shared item tag is retained when only another version still references it`() {
        val snapshot = snapshot(
            tags = listOf(versionTag(), itemTag(), otherVersionTag()),
            versions = listOf(targetVersion(), otherVersion()),
            migrations = listOf(MigrationHistoryEntry(OTHER_HISTORY_ID, ITEM_TAG_ID, OTHER_VERSION_ID)),
        )

        assertEquals(
            MigrationDownRecordingDecision.Complete(retainedTagId = ITEM_TAG_ID),
            reconciler.assess(snapshot, TARGET_VERSION_ID, migrationId),
        )
    }

    @Test
    fun `duplicate and inconsistent history evidence fails closed`() {
        val duplicateTargetHistory = snapshot(
            tags = listOf(versionTag(), itemTag()),
            versions = listOf(targetVersion()),
            migrations = listOf(
                MigrationHistoryEntry(TARGET_HISTORY_ID, ITEM_TAG_ID, TARGET_VERSION_ID),
                MigrationHistoryEntry(TARGET_HISTORY_ID + 1, ITEM_TAG_ID, TARGET_VERSION_ID),
            ),
        )
        assertIs<MigrationDownRecordingDecision.Inconsistent>(
            reconciler.assess(duplicateTargetHistory, TARGET_VERSION_ID, migrationId),
        )

        val duplicateItemTags = snapshot(
            tags = listOf(versionTag(), itemTag(), itemTag(ITEM_TAG_ID + 1)),
            versions = listOf(targetVersion()),
        )
        assertIs<MigrationDownRecordingDecision.Inconsistent>(
            reconciler.assess(duplicateItemTags, TARGET_VERSION_ID, migrationId),
        )

        val wrongVersionTag = snapshot(
            tags = listOf(MigrationHistoryTag(VERSION_TAG_ID, "v1", MigrationTagType.ITEM.code), itemTag()),
            versions = listOf(targetVersion()),
        )
        assertIs<MigrationDownRecordingDecision.Inconsistent>(
            reconciler.assess(wrongVersionTag, TARGET_VERSION_ID, migrationId),
        )
    }

    @Test
    fun `existing target history is the only scheduled deletion`() {
        val snapshot = snapshot(
            tags = listOf(versionTag(), itemTag()),
            versions = listOf(targetVersion()),
            migrations = listOf(MigrationHistoryEntry(TARGET_HISTORY_ID, ITEM_TAG_ID, TARGET_VERSION_ID)),
        )

        assertEquals(
            MigrationDownRecordingDecision.DeleteTargetHistory(ITEM_TAG_ID, TARGET_VERSION_ID),
            reconciler.assess(snapshot, TARGET_VERSION_ID, migrationId),
        )
    }

    private fun snapshot(
        tags: List<MigrationHistoryTag>,
        versions: List<MigrationHistoryVersion>,
        migrations: List<MigrationHistoryEntry> = emptyList(),
    ) = MigrationVersionHistorySnapshot(tags, versions, migrations)

    private fun versionTag() = MigrationHistoryTag(VERSION_TAG_ID, "v1", MigrationTagType.VERSION.code)

    private fun itemTag(id: Int = ITEM_TAG_ID) = MigrationHistoryTag(id, migrationId.value, MigrationTagType.ITEM.code)

    private fun otherVersionTag() = MigrationHistoryTag(OTHER_VERSION_TAG_ID, "v2", MigrationTagType.VERSION.code)

    private fun targetVersion() = MigrationHistoryVersion(
        TARGET_VERSION_ID,
        VERSION_TAG_ID,
        MigrationVersionState.STATUS_ROLLBACK_STARTED,
    )

    private fun otherVersion() = MigrationHistoryVersion(
        OTHER_VERSION_ID,
        OTHER_VERSION_TAG_ID,
        MigrationVersionState.STATUS_MIGRATE_COMPLETED,
    )

    private companion object {
        const val TARGET_VERSION_ID = 10
        const val OTHER_VERSION_ID = 20
        const val VERSION_TAG_ID = 100
        const val OTHER_VERSION_TAG_ID = 200
        const val ITEM_TAG_ID = 300
        const val TARGET_HISTORY_ID = 400
        const val OTHER_HISTORY_ID = 500
    }
}
