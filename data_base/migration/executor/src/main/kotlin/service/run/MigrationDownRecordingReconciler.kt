package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import model.version_history.MigrationVersionHistorySnapshot

/**
 * The next metadata-only action for a DOWN attempt whose checkpoint has already been validated as
 * RECORD_PENDING and whose Schema `down()` completed successfully.
 *
 * This class only classifies a persisted snapshot. Its caller remains responsible for acquiring the
 * Migration lock, validating the checkpoint/Version/registration, and executing at most the returned
 * metadata operation on the same Operation Scope. It never invokes Schema code.
 */
internal sealed interface MigrationDownRecordingDecision {
    data class DeleteTargetHistory(val tagId: Int, val versionId: Int) : MigrationDownRecordingDecision

    data class DeleteUnreferencedItemTag(val tagId: Int) : MigrationDownRecordingDecision

    /** [retainedTagId] is non-null when another Version still references the shared ITEM Tag. */
    data class Complete(val retainedTagId: Int?) : MigrationDownRecordingDecision

    data class Inconsistent(val reason: String) : MigrationDownRecordingDecision
}

/**
 * Classifies DOWN recording progress from the current persisted snapshot.
 *
 * Preconditions are deliberately explicit: the caller must have confirmed under the active
 * `SystemLock.MIGRATION` that this is the same Version and cursor, that the unique ITEM Tag and its
 * target History were valid before Schema execution, and that the checkpoint is RECORD_PENDING.
 * Under those conditions, a missing Tag plus missing History is the terminal state produced by the
 * existing history-first, then-unreferenced-Tag deletion order. It is therefore complete without
 * another DML operation. The lock coordinates managed Migration operations; this does not protect
 * against out-of-band SQL that ignores the lock.
 */
internal class MigrationDownRecordingReconciler {
    fun assess(
        snapshot: MigrationVersionHistorySnapshot,
        versionId: Int,
        migrationId: MigrationIdentity,
    ): MigrationDownRecordingDecision {
        if (versionId <= 0) return inconsistent("DOWN recording Version id must be positive")

        if (snapshot.tags.any { it.id <= 0 } || snapshot.versions.any { it.id <= 0 } ||
            snapshot.migrations.any { it.id <= 0 || it.tagId <= 0 || it.versionId <= 0 }
        ) {
            return inconsistent("DOWN recording snapshot contains a non-positive persisted id")
        }
        if (snapshot.tags.groupBy { it.id }.any { it.value.size > 1 }) {
            return inconsistent("DOWN recording snapshot contains duplicate Tag ids")
        }
        if (snapshot.versions.groupBy { it.id }.any { it.value.size > 1 }) {
            return inconsistent("DOWN recording snapshot contains duplicate Version ids")
        }
        if (snapshot.migrations.groupBy { it.id }.any { it.value.size > 1 }) {
            return inconsistent("DOWN recording snapshot contains duplicate History ids")
        }

        val tagsById = snapshot.tags.associateBy { it.id }
        val versionsById = snapshot.versions.associateBy { it.id }
        if (snapshot.migrations.any { it.tagId !in tagsById || it.versionId !in versionsById }) {
            return inconsistent("DOWN recording snapshot contains a dangling History reference")
        }

        val targetVersions = snapshot.versions.filter { it.id == versionId }
        if (targetVersions.size != 1) {
            return inconsistent("Expected exactly one target Version $versionId")
        }
        val targetVersionTag = tagsById[targetVersions.single().tagId]
            ?: return inconsistent("Target Version $versionId references a missing Tag")
        if (targetVersionTag.type != MigrationTagType.VERSION.code) {
            return inconsistent("Target Version $versionId references a non-Version Tag")
        }

        val namedTags = snapshot.tags.filter { it.name == migrationId.value }
        val itemTags = namedTags.filter { it.type == MigrationTagType.ITEM.code }
        if (itemTags.size > 1) {
            return inconsistent("Duplicate Migration item Tags exist for '${migrationId.value}'")
        }
        if (itemTags.isEmpty()) {
            if (namedTags.isNotEmpty()) {
                return inconsistent("Migration '${migrationId.value}' has only non-item Tag evidence")
            }
            // With a valid RECORD_PENDING precondition, this is the state after the existing
            // history-first recording path deleted its unreferenced Tag. The snapshot-wide dangling
            // reference check above rejects contradictory orphan History rows.
            return MigrationDownRecordingDecision.Complete(retainedTagId = null)
        }

        val tag = itemTags.single()
        val histories = snapshot.migrations.filter { it.tagId == tag.id }
        histories.forEach { history ->
            val ownerVersion = versionsById.getValue(history.versionId)
            val ownerTag = tagsById[ownerVersion.tagId]
                ?: return inconsistent("History ${history.id} belongs to a Version with a missing Tag")
            if (ownerTag.type != MigrationTagType.VERSION.code) {
                return inconsistent("History ${history.id} belongs to a Version with a non-Version Tag")
            }
        }

        val targetHistories = histories.filter { it.versionId == versionId }
        if (targetHistories.size > 1) {
            return inconsistent("Duplicate target History exists for Migration '${migrationId.value}'")
        }
        val otherHistories = histories.filter { it.versionId != versionId }
        if (otherHistories.size > 1) {
            return inconsistent("Migration '${migrationId.value}' has multiple other-Version History rows")
        }

        if (targetHistories.size == 1) {
            return MigrationDownRecordingDecision.DeleteTargetHistory(tag.id, versionId)
        }
        if (otherHistories.size == 1) {
            // The target History is already gone. Preserve a shared Tag while its other reference
            // remains; this is the same rule used by runDownInternal after deleting target History.
            return MigrationDownRecordingDecision.Complete(retainedTagId = tag.id)
        }
        return MigrationDownRecordingDecision.DeleteUnreferencedItemTag(tag.id)
    }

    private fun inconsistent(reason: String) = MigrationDownRecordingDecision.Inconsistent(reason)
}
