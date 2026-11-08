package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import model.version_history.MigrationHistoryEntry
import model.version_history.MigrationHistoryTag
import model.version_history.MigrationHistoryVersion
import model.version_history.MigrationVersionHistorySnapshot
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService
import java.util.ArrayList
import java.util.Collections

/**
 * Shared consistency rules for persisted history used by both planning and per-Version rollback.
 * Unreferenced Tags are intentional: Version Tags survive `transactionDown`, and `runUp` accepts
 * and reuses an existing ITEM Tag when it has no applied-history row.
 */
internal class MigrationVersionHistoryValidator(
    private val preparationService: MigrationVersionPreparationService = MigrationVersionPreparationService(),
) {
    fun validateConfiguration(
        configuration: MigrationConfiguration,
        snapshot: MigrationVersionHistorySnapshot,
    ): MigrationVersionHistoryState {
        val indexed = indexAndValidateRows(snapshot)
        val configuredIndexes = configuration.registrations.withIndex().associate { it.value.name to it.index }
        val persistedInIdOrder = snapshot.versions.sortedBy(MigrationHistoryVersion::id)

        persistedInIdOrder.forEachIndexed { expectedIndex, persisted ->
            val versionTag = indexed.tagsById.getValue(persisted.tagId)
            val configIndex = configuredIndexes[versionTag.name]
                ?: invalid("Persisted Migration Version '${versionTag.name}' is not present in the configuration")
            if (configIndex != expectedIndex) {
                val expectedName = configuration.registrations.getOrNull(expectedIndex)?.name ?: "no Version"
                invalid(
                    "Persisted Version ID order conflicts with MigrationConfiguration: " +
                        "found '${versionTag.name}' where '$expectedName' was expected",
                )
            }
        }

        val applied = persistedInIdOrder.mapIndexed { index, persisted ->
            val configuredVersion = configuration.registrations[index]
            val prepared = preparationService.prepare(configuredVersion)
            validateExpectedMigrations(indexed, persisted.id, prepared.migrations)
            prepared
        }
        return MigrationVersionHistoryState(applied)
    }

    /** Validates one Version using the same row and migration matching rules as whole-history validation. */
    fun validateVersion(
        snapshot: MigrationVersionHistorySnapshot,
        versionId: Int,
        migrations: List<RegisteredMigration>,
    ) {
        val indexed = indexAndValidateRows(snapshot)
        val versionRows = snapshot.versions.filter { it.id == versionId }
        if (versionRows.size != 1) {
            invalid("Expected exactly one persisted Migration Version with id $versionId; found ${versionRows.size}")
        }
        validateExpectedMigrations(indexed, versionId, migrations)
    }

    private fun indexAndValidateRows(snapshot: MigrationVersionHistorySnapshot): IndexedHistory {
        snapshot.tags.forEach { requirePositive(it.id, "Migration Tag id") }
        snapshot.versions.forEach { requirePositive(it.id, "Migration Version id") }
        snapshot.migrations.forEach { requirePositive(it.id, "Applied Migration id") }

        val duplicateTagIds = snapshot.tags.groupBy(MigrationHistoryTag::id).filterValues { it.size > 1 }
        if (duplicateTagIds.isNotEmpty()) invalid("Migration history contains duplicate Tag ids")
        val tagsById = snapshot.tags.associateBy(MigrationHistoryTag::id)

        val duplicateTagNames = snapshot.tags.groupBy { it.type to it.name }.filterValues { it.size > 1 }
        if (duplicateTagNames.isNotEmpty()) invalid("Migration history contains duplicate Tag names and types")
        snapshot.tags.forEach { tag ->
            if (tag.name.isBlank()) invalid("Migration history contains a blank Tag name")
            if (tag.type != MigrationTagType.VERSION.code && tag.type != MigrationTagType.ITEM.code) {
                invalid("Migration history contains unsupported persisted Tag type ${tag.type}")
            }
        }

        val duplicateVersionIds = snapshot.versions.groupBy(MigrationHistoryVersion::id).filterValues { it.size > 1 }
        if (duplicateVersionIds.isNotEmpty()) invalid("Migration history contains duplicate Version ids")
        val duplicateVersionTags = snapshot.versions.groupBy(MigrationHistoryVersion::tagId).filterValues { it.size > 1 }
        if (duplicateVersionTags.isNotEmpty()) invalid("More than one persisted Version references the same Version Tag")
        snapshot.versions.forEach { version ->
            val tag = tagsById[version.tagId]
                ?: invalid("Persisted Migration Version ${version.id} references missing Tag ${version.tagId}")
            if (tag.type != MigrationTagType.VERSION.code) {
                invalid("Persisted Migration Version ${version.id} references a non-Version Tag")
            }
        }

        val duplicateAppliedIds = snapshot.migrations.groupBy(MigrationHistoryEntry::id).filterValues { it.size > 1 }
        if (duplicateAppliedIds.isNotEmpty()) invalid("Migration history contains duplicate applied record ids")
        val versionsById = snapshot.versions.associateBy(MigrationHistoryVersion::id)
        snapshot.migrations.forEach { applied ->
            if (versionsById[applied.versionId] == null) {
                invalid("Applied Migration ${applied.id} references missing Version ${applied.versionId}")
            }
            val tag = tagsById[applied.tagId]
                ?: invalid("Applied Migration ${applied.id} references missing Tag ${applied.tagId}")
            if (tag.type != MigrationTagType.ITEM.code) {
                invalid("Applied Migration ${applied.id} references a non-Migration item Tag")
            }
        }
        if (snapshot.migrations.groupBy { it.versionId to it.tagId }.any { it.value.size > 1 }) {
            invalid("Duplicate applied Migration history exists within a Version")
        }
        if (snapshot.migrations.groupBy(MigrationHistoryEntry::tagId).any { it.value.size > 1 }) {
            invalid("A Migration is recorded under more than one persisted Version")
        }

        val itemTagsByName = snapshot.tags
            .filter { it.type == MigrationTagType.ITEM.code }
            .groupBy(MigrationHistoryTag::name)
        if (itemTagsByName.any { it.value.size > 1 }) {
            invalid("Migration history contains duplicate Migration item Tags")
        }

        return IndexedHistory(
            tagsById = tagsById,
            itemTagsByName = itemTagsByName,
            migrationsByVersion = snapshot.migrations.groupBy(MigrationHistoryEntry::versionId),
            migrationsByTag = snapshot.migrations.groupBy(MigrationHistoryEntry::tagId),
        )
    }

    private fun validateExpectedMigrations(
        indexed: IndexedHistory,
        versionId: Int,
        expectedMigrations: List<RegisteredMigration>,
    ) {
        val expectedIds = expectedMigrations.map { it.id.value }
        if (expectedIds.toSet().size != expectedIds.size) {
            invalid("Prepared Migration Version contains duplicate Migration IDs")
        }

        val currentHistory = indexed.migrationsByVersion[versionId].orEmpty()
        for (row in currentHistory) {
            val tag = indexed.tagsById.getValue(row.tagId)
            if (tag.name !in expectedIds) {
                invalid("Applied Migration '${tag.name}' in Version $versionId is not present in the configuration")
            }
        }

        for (migrationId in expectedIds) {
            val tag = indexed.itemTagsByName[migrationId]?.singleOrNull()
                ?: invalid("Configured Migration '$migrationId' does not have exactly one persisted item Tag")
            val rows = indexed.migrationsByTag[tag.id].orEmpty()
            if (rows.any { it.versionId != versionId }) {
                invalid("Migration '$migrationId' is registered under a different persisted Version")
            }
            if (rows.size != 1) {
                invalid("Configured Migration '$migrationId' has ${rows.size} history rows in Version $versionId")
            }
        }

        if (currentHistory.size != expectedMigrations.size) {
            invalid(
                "Migration history count for Version $versionId was ${currentHistory.size}; " +
                    "expected ${expectedMigrations.size}",
            )
        }

    }

    private fun requirePositive(id: Int, description: String) {
        if (id <= 0) invalid("$description must be positive, found $id")
    }

    private fun invalid(message: String): Nothing = throw IllegalStateException(message)

    private data class IndexedHistory(
        val tagsById: Map<Int, MigrationHistoryTag>,
        val itemTagsByName: Map<String, List<MigrationHistoryTag>>,
        val migrationsByVersion: Map<Int, List<MigrationHistoryEntry>>,
        val migrationsByTag: Map<Int, List<MigrationHistoryEntry>>,
    )
}

internal class MigrationVersionHistoryState(
    appliedVersions: List<PreparedMigrationVersion>,
) {
    val appliedVersions: List<PreparedMigrationVersion> =
        Collections.unmodifiableList(ArrayList(appliedVersions))
}
