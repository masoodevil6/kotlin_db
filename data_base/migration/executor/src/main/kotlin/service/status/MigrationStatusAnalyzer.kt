package gog.my_project.data_base_migration.executor.service.status

import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import model.migration_status.MigrationItemStatus
import model.migration_status.MigrationStatusReport
import model.migration_status.MigrationStatusRow
import model.migration_status.MigrationStatusSnapshot
import model.migration_status.MigrationVersionStatus
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Compares immutable configuration and status inputs without performing I/O or migration work. */
internal class MigrationStatusAnalyzer(
    private val prepareVersion: (MigrationVersion) -> PreparedMigrationVersion =
        MigrationVersionPreparationService()::prepare,
) {
    fun analyze(
        configuration: MigrationConfiguration,
        snapshot: MigrationStatusSnapshot,
    ): MigrationStatusReport {
        // The preparation service only flattens registration nodes; it does not instantiate migrations.
        // Deliberately do not catch Throwable here: fatal Errors must propagate to the caller unchanged.
        val preparedVersions = configuration.registrations.map(prepareVersion)
        val versionNames = configuration.registrations.mapTo(linkedSetOf()) { it.name }
        val migrationOwners = LinkedHashMap<String, String>()
        // MigrationConfiguration validates MigrationIdentity uniqueness across every Version
        // and nested Group, so each id has exactly one owner here.
        preparedVersions.forEach { prepared ->
            prepared.migrations.forEach { migration ->
                migrationOwners[migration.id.value] = prepared.version.name
            }
        }

        val issues = IssueCollector()
        val facts = index(snapshot, migrationOwners, issues)
        validateTagFacts(facts, versionNames, migrationOwners, issues)
        validateVersionFacts(facts, versionNames, migrationOwners, issues)
        validateHistoryFacts(facts, migrationOwners, issues)
        validateVersionOrder(configuration, facts, issues)
        validateUnexpectedHistory(preparedVersions, facts, migrationOwners, issues)
        validateMissingExpectedHistory(preparedVersions, facts, migrationOwners, issues)

        val configuredVersions = preparedVersions.map { prepared ->
            val name = prepared.version.name
            val versionTags = facts.tags.filter {
                it.typeCode == MigrationTagType.VERSION.code && it.name == name
            }
            val versionTagIds = versionTags.map(TagFact::id).distinct()
            val versionIds = facts.versions
                .filter { it.tagId in versionTagIds || (it.tagName == name && it.tagTypeCode == MigrationTagType.VERSION.code) }
                .map(VersionFact::id)
                .distinct()

            val itemEntries = prepared.migrations.map { registered ->
                configuredMigrationEntry(
                    registered,
                    name,
                    versionIds,
                    facts,
                    issues,
                )
            }
            val versionIssues = issues.forVersion(name)
            val invalid = versionIssues.isNotEmpty() || itemEntries.any { it.status == MigrationItemStatus.INVALID }
            val status = when {
                invalid -> MigrationVersionStatus.INVALID
                versionIds.isEmpty() -> MigrationVersionStatus.PENDING
                itemEntries.all { it.status == MigrationItemStatus.APPLIED } -> MigrationVersionStatus.APPLIED
                else -> MigrationVersionStatus.INVALID
            }
            MigrationStatusReport.ConfiguredVersion(
                name = name,
                status = status,
                versionTagIds = versionTagIds,
                versionIds = versionIds,
                migrations = itemEntries,
                issues = versionIssues,
            )
        }

        val unknownVersions = buildUnknownVersions(facts, versionNames, issues)
        val unknownMigrations = buildUnknownMigrations(facts, migrationOwners, issues)
        val unknownTags = buildUnknownTags(facts, issues)
        return MigrationStatusReport(
            versions = configuredVersions,
            unknownVersions = unknownVersions,
            unknownMigrations = unknownMigrations,
            unknownTags = unknownTags,
            issues = issues.values(),
        )
    }

    private fun index(
        snapshot: MigrationStatusSnapshot,
        migrationOwners: Map<String, String>,
        issues: IssueCollector,
    ): HistoryFacts {
        val tagVariants = linkedMapOf<Int, LinkedHashSet<TagFact>>()
        val versionVariants = linkedMapOf<Int, LinkedHashSet<VersionFact>>()
        val historyVariants = linkedMapOf<Int, LinkedHashSet<HistoryFact>>()

        snapshot.rows.forEach { row ->
            tagVariants.getOrPut(row.tagId, ::linkedSetOf).add(TagFact(row.tagId, row.tagName, row.tagType))

            row.tagVersionId?.let { versionId ->
                versionVariants.getOrPut(versionId, ::linkedSetOf)
                    .add(VersionFact(versionId, row.tagId, row.tagName, row.tagType))
            }
            row.appliedVersionId?.let { versionId ->
                versionVariants.getOrPut(versionId, ::linkedSetOf)
                    .add(
                        VersionFact(
                            versionId,
                            row.appliedVersionTagId,
                            row.appliedVersionTagName,
                            row.appliedVersionTagType,
                        ),
                    )
            }

            if (row.migrationHistoryId != null) {
                historyVariants.getOrPut(row.migrationHistoryId, ::linkedSetOf)
                    .add(
                        HistoryFact(
                            id = row.migrationHistoryId,
                            tagId = row.tagId,
                            tagName = row.tagName,
                            tagTypeCode = row.tagType,
                            versionReferenceId = row.appliedVersionReferenceId,
                            resolvedVersionId = row.appliedVersionId,
                            versionTagId = row.appliedVersionTagId,
                            versionTagName = row.appliedVersionTagName,
                            versionTagTypeCode = row.appliedVersionTagType,
                        ),
                    )
            } else if (
                row.appliedVersionReferenceId != null || row.appliedVersionId != null ||
                row.appliedVersionTagId != null || row.appliedVersionTagName != null ||
                row.appliedVersionTagType != null
            ) {
                val owner = migrationOwners[row.tagName]
                issues.add(
                    MigrationStatusReport.Issue.Code.INCOMPLETE_HISTORY_RECORD,
                    "Status row contains Version/history fields without a Migration history record",
                    affectedVersionNames = listOfNotNull(owner),
                    affectedMigrationIds = listOfNotNull(row.tagName.takeIf { row.tagType == MigrationTagType.ITEM.code }),
                    tagIds = listOf(row.tagId),
                    versionIds = listOfNotNull(row.appliedVersionId),
                    versionReferenceIds = listOfNotNull(row.appliedVersionReferenceId),
                )
            }
        }

        tagVariants.forEach { (id, variants) ->
            if (variants.size > 1) {
                val names = variants.map(TagFact::name).distinct()
                val types = variants.map(TagFact::typeCode).distinct()
                val relatedVersions = variants.flatMap { tag ->
                    when (tag.typeCode) {
                        MigrationTagType.VERSION.code -> listOf(tag.name)
                        MigrationTagType.ITEM.code -> listOfNotNull(migrationOwners[tag.name])
                        else -> emptyList()
                    }
                }.distinct()
                val relatedItems = variants.filter { it.typeCode == MigrationTagType.ITEM.code }
                    .map(TagFact::name)
                issues.add(
                    MigrationStatusReport.Issue.Code.INCONSISTENT_TAG_ID,
                    "Persisted Tag id $id has inconsistent names or types: names=$names, types=$types",
                    affectedVersionNames = relatedVersions,
                    affectedMigrationIds = relatedItems,
                    tagIds = listOf(id),
                )
            }
        }
        versionVariants.forEach { (id, variants) ->
            if (variants.size > 1) {
                issues.add(
                    MigrationStatusReport.Issue.Code.INCONSISTENT_VERSION_RECORD,
                    "Persisted Version id $id is associated with more than one Tag projection",
                    affectedVersionNames = variants.mapNotNull { fact ->
                        fact.tagName.takeIf { fact.tagTypeCode == MigrationTagType.VERSION.code }
                    },
                    affectedMigrationIds = variants.mapNotNull { fact ->
                        fact.tagName.takeIf { fact.tagTypeCode == MigrationTagType.ITEM.code }
                    },
                    tagIds = variants.mapNotNull(VersionFact::tagId),
                    versionIds = listOf(id),
                )
            }
        }
        historyVariants.forEach { (id, variants) ->
            if (variants.size > 1) {
                val itemNames = variants.mapNotNull { fact ->
                    fact.tagName.takeIf { fact.tagTypeCode == MigrationTagType.ITEM.code }
                }
                val versionNames = variants.mapNotNull { fact ->
                    fact.versionTagName.takeIf { fact.versionTagTypeCode == MigrationTagType.VERSION.code }
                }
                issues.add(
                    MigrationStatusReport.Issue.Code.INCONSISTENT_HISTORY_RECORD,
                    "Applied Migration history id $id has conflicting Tag or Version projections",
                    affectedVersionNames = (itemNames.mapNotNull(migrationOwners::get) + versionNames).distinct(),
                    affectedMigrationIds = itemNames,
                    tagIds = variants.map(HistoryFact::tagId),
                    versionIds = variants.mapNotNull(HistoryFact::resolvedVersionId),
                    historyRecordIds = listOf(id),
                    versionReferenceIds = variants.mapNotNull(HistoryFact::versionReferenceId),
                )
            }
        }

        return HistoryFacts(
            tags = tagVariants.values.flatMap { it },
            versions = versionVariants.values.flatMap { it },
            histories = historyVariants.values.flatMap { it },
        )
    }

    private fun validateTagFacts(
        facts: HistoryFacts,
        configuredVersionNames: Set<String>,
        migrationOwners: Map<String, String>,
        issues: IssueCollector,
    ) {
        facts.tags.forEach { tag ->
            // For an unsupported type, the persisted category is unknowable. Associate the issue
            // with any configured identity sharing its name so malformed data cannot look pending.
            val relatedVersion = when (tag.typeCode) {
                MigrationTagType.VERSION.code -> tag.name
                MigrationTagType.ITEM.code -> migrationOwners[tag.name]
                else -> tag.name.takeIf(configuredVersionNames::contains) ?: migrationOwners[tag.name]
            }
            val relatedMigration = tag.name.takeIf {
                tag.typeCode == MigrationTagType.ITEM.code ||
                    (tag.typeCode != MigrationTagType.VERSION.code && migrationOwners.containsKey(it))
            }
            if (tag.id <= 0) {
                issues.add(
                    MigrationStatusReport.Issue.Code.INVALID_PERSISTED_ID,
                    "Migration Tag id must be positive, found ${tag.id}",
                    affectedVersionNames = listOfNotNull(relatedVersion),
                    affectedMigrationIds = listOfNotNull(relatedMigration),
                    tagIds = listOf(tag.id),
                )
            }
            if (tag.name.isBlank()) {
                issues.add(
                    MigrationStatusReport.Issue.Code.BLANK_TAG_NAME,
                    "Persisted Migration Tag ${tag.id} has a blank name",
                    affectedVersionNames = listOfNotNull(relatedVersion),
                    affectedMigrationIds = listOfNotNull(relatedMigration),
                    tagIds = listOf(tag.id),
                )
            }
            if (tag.typeCode != MigrationTagType.VERSION.code && tag.typeCode != MigrationTagType.ITEM.code) {
                issues.add(
                    MigrationStatusReport.Issue.Code.INVALID_TAG_TYPE,
                    "Persisted Migration Tag ${tag.id} has unsupported type code ${tag.typeCode}",
                    affectedVersionNames = listOfNotNull(relatedVersion),
                    affectedMigrationIds = listOfNotNull(relatedMigration),
                    tagIds = listOf(tag.id),
                )
            }
            when (tag.typeCode) {
                MigrationTagType.VERSION.code -> if (tag.name.isNotBlank() && tag.name.length > 255) {
                    issues.add(
                        MigrationStatusReport.Issue.Code.INVALID_VERSION_NAME,
                        "Persisted Version Tag ${tag.id} exceeds the 255 character limit",
                        affectedVersionNames = listOfNotNull(relatedVersion),
                        tagIds = listOf(tag.id),
                    )
                }
                MigrationTagType.ITEM.code -> try {
                    MigrationIdentity.fromValue(tag.name)
                } catch (failure: IllegalArgumentException) {
                    issues.add(
                        MigrationStatusReport.Issue.Code.INVALID_MIGRATION_ID,
                        "Persisted Migration item Tag ${tag.id} has invalid identity '${tag.name}': ${failure.message}",
                        affectedVersionNames = listOfNotNull(relatedVersion),
                        affectedMigrationIds = listOfNotNull(relatedMigration),
                        tagIds = listOf(tag.id),
                    )
                }
            }
        }

        facts.tags.groupBy { it.typeCode to it.name }.values.forEach { sameIdentity ->
            val tagIds = sameIdentity.map(TagFact::id).distinct()
            if (tagIds.size > 1) {
                val tag = sameIdentity.first()
                val affectedVersion = tag.name.takeIf { tag.typeCode == MigrationTagType.VERSION.code }
                val affectedMigration = tag.name.takeIf {
                    tag.typeCode == MigrationTagType.ITEM.code
                }
                issues.add(
                    MigrationStatusReport.Issue.Code.DUPLICATE_TAG,
                    "Persisted Tag name '${tag.name}' with type ${tag.typeCode} is stored under Tag ids $tagIds",
                    affectedVersionNames = listOfNotNull(affectedVersion, affectedMigration?.let(migrationOwners::get)),
                    affectedMigrationIds = listOfNotNull(affectedMigration),
                    tagIds = tagIds,
                )
            }
        }
    }

    private fun validateVersionFacts(
        facts: HistoryFacts,
        configuredVersionNames: Set<String>,
        migrationOwners: Map<String, String>,
        issues: IssueCollector,
    ) {
        facts.versions.forEach { version ->
            // Resolve by identity name rather than trusting the persisted type code: malformed
            // associations can still identify the configured Version or Migration they affect.
            val associatedTagNames = (
                listOfNotNull(version.tagName) + version.tagId?.let { tagId ->
                    facts.tags.filter { it.id == tagId }.map(TagFact::name)
                }.orEmpty()
                ).distinct()
            val relatedVersionNames = associatedTagNames.filter {
                it in configuredVersionNames ||
                    (it == version.tagName && version.tagTypeCode == MigrationTagType.VERSION.code)
            }
            val relatedMigrationIds = associatedTagNames.filter(migrationOwners::containsKey)
            val affectedVersionNames =
                (relatedVersionNames + relatedMigrationIds.mapNotNull(migrationOwners::get)).distinct()
            if (version.id <= 0) {
                issues.add(
                    MigrationStatusReport.Issue.Code.INVALID_PERSISTED_ID,
                    "Migration Version id must be positive, found ${version.id}",
                    affectedVersionNames = affectedVersionNames,
                    affectedMigrationIds = relatedMigrationIds,
                    tagIds = listOfNotNull(version.tagId),
                    versionIds = listOf(version.id),
                )
            }
            if (version.tagId == null || facts.tags.none { it.id == version.tagId }) {
                issues.add(
                    MigrationStatusReport.Issue.Code.BROKEN_VERSION_TAG_REFERENCE,
                    "Migration Version ${version.id} has no resolvable Version Tag",
                    affectedVersionNames = affectedVersionNames,
                    affectedMigrationIds = relatedMigrationIds,
                    tagIds = listOfNotNull(version.tagId),
                    versionIds = listOf(version.id),
                )
            }
            if (version.tagName == null || version.tagTypeCode == null || version.tagId == null) {
                issues.add(
                    MigrationStatusReport.Issue.Code.BROKEN_VERSION_TAG_REFERENCE,
                    "Migration Version ${version.id} has incomplete Version Tag metadata",
                    affectedVersionNames = affectedVersionNames,
                    affectedMigrationIds = relatedMigrationIds,
                    tagIds = listOfNotNull(version.tagId),
                    versionIds = listOf(version.id),
                )
            } else if (version.tagTypeCode != MigrationTagType.VERSION.code) {
                issues.add(
                    MigrationStatusReport.Issue.Code.VERSION_ATTACHED_TO_INVALID_TAG_TYPE,
                    "Migration Version ${version.id} references Tag ${version.tagId} with non-Version type ${version.tagTypeCode}",
                    affectedVersionNames = affectedVersionNames,
                    affectedMigrationIds = relatedMigrationIds,
                    tagIds = listOf(version.tagId),
                    versionIds = listOf(version.id),
                )
            }
        }

        facts.versions.filter { it.tagId != null }.groupBy { it.tagId }.forEach { (tagId, records) ->
            val versionIds = records.map(VersionFact::id).distinct()
            if (versionIds.size > 1) {
                val tagName = records.firstNotNullOfOrNull { record ->
                    record.tagName.takeIf { record.tagTypeCode == MigrationTagType.VERSION.code }
                }
                val migrationName = records.firstNotNullOfOrNull { record ->
                    record.tagName.takeIf { record.tagTypeCode == MigrationTagType.ITEM.code }
                }
                issues.add(
                    MigrationStatusReport.Issue.Code.DUPLICATE_VERSION_RECORD,
                    "Version Tag $tagId is referenced by multiple Migration Version records $versionIds",
                    affectedVersionNames = listOfNotNull(tagName),
                    affectedMigrationIds = listOfNotNull(migrationName),
                    tagIds = listOf(tagId!!),
                    versionIds = versionIds,
                )
            }
        }
    }

    private fun validateHistoryFacts(
        facts: HistoryFacts,
        migrationOwners: Map<String, String>,
        issues: IssueCollector,
    ) {
        facts.histories.forEach { history ->
            val expectedVersion = migrationOwners[history.tagName]
                .takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }
            val actualVersion = history.versionTagName.takeIf { history.versionTagTypeCode == MigrationTagType.VERSION.code }
            if (history.id <= 0 || history.tagId <= 0) {
                issues.add(
                    MigrationStatusReport.Issue.Code.INVALID_PERSISTED_ID,
                    "Applied Migration history and Tag ids must be positive (history=${history.id}, tag=${history.tagId})",
                    affectedVersionNames = listOfNotNull(expectedVersion, actualVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOf(history.tagId),
                    versionIds = listOfNotNull(history.resolvedVersionId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOfNotNull(history.versionReferenceId),
                )
            }
            if (history.tagTypeCode != MigrationTagType.ITEM.code) {
                issues.add(
                    MigrationStatusReport.Issue.Code.HISTORY_ATTACHED_TO_INVALID_TAG_TYPE,
                    "Applied Migration history ${history.id} is attached to Tag ${history.tagId} with non-item type ${history.tagTypeCode}",
                    affectedVersionNames = listOfNotNull(expectedVersion, actualVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOf(history.tagId),
                    versionIds = listOfNotNull(history.resolvedVersionId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOfNotNull(history.versionReferenceId),
                )
            }
            if (history.versionReferenceId == null) {
                issues.add(
                    MigrationStatusReport.Issue.Code.BROKEN_VERSION_REFERENCE,
                    "Applied Migration history ${history.id} has no Version reference",
                    affectedVersionNames = listOfNotNull(expectedVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOf(history.tagId),
                    historyRecordIds = listOf(history.id),
                )
            } else if (history.versionReferenceId <= 0) {
                issues.add(
                    MigrationStatusReport.Issue.Code.INVALID_PERSISTED_ID,
                    "Applied Migration history ${history.id} has non-positive Version reference ${history.versionReferenceId}",
                    affectedVersionNames = listOfNotNull(expectedVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOf(history.tagId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOf(history.versionReferenceId),
                )
            }
            if (history.versionReferenceId != null && history.resolvedVersionId == null) {
                issues.add(
                    MigrationStatusReport.Issue.Code.BROKEN_VERSION_REFERENCE,
                    "Applied Migration history ${history.id} references missing Version ${history.versionReferenceId}",
                    affectedVersionNames = listOfNotNull(expectedVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOf(history.tagId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOf(history.versionReferenceId),
                )
            }
            if (history.versionReferenceId != null && history.resolvedVersionId != null &&
                history.versionReferenceId != history.resolvedVersionId
            ) {
                issues.add(
                    MigrationStatusReport.Issue.Code.VERSION_REFERENCE_MISMATCH,
                    "Applied Migration history ${history.id} references Version ${history.versionReferenceId} but resolved to ${history.resolvedVersionId}",
                    affectedVersionNames = listOfNotNull(expectedVersion, actualVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOf(history.tagId),
                    versionIds = listOf(history.resolvedVersionId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOf(history.versionReferenceId),
                )
            }
            if (history.resolvedVersionId != null && (
                    history.versionTagId == null || history.versionTagName == null || history.versionTagTypeCode == null
                )
            ) {
                issues.add(
                    MigrationStatusReport.Issue.Code.BROKEN_VERSION_TAG_REFERENCE,
                    "Applied Migration history ${history.id} resolves Version ${history.resolvedVersionId} without complete Tag metadata",
                    affectedVersionNames = listOfNotNull(expectedVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOfNotNull(history.versionTagId, history.tagId),
                    versionIds = listOf(history.resolvedVersionId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOfNotNull(history.versionReferenceId),
                )
            }
            if (history.versionTagTypeCode != null && history.versionTagTypeCode != MigrationTagType.VERSION.code) {
                issues.add(
                    MigrationStatusReport.Issue.Code.VERSION_ATTACHED_TO_INVALID_TAG_TYPE,
                    "Applied Migration history ${history.id} resolves through Tag ${history.versionTagId} with non-Version type ${history.versionTagTypeCode}",
                    affectedVersionNames = listOfNotNull(expectedVersion, actualVersion),
                    affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                    tagIds = listOfNotNull(history.tagId, history.versionTagId),
                    versionIds = listOfNotNull(history.resolvedVersionId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOfNotNull(history.versionReferenceId),
                )
            }
            if (history.versionTagId != null) {
                val tag = facts.tags.firstOrNull { it.id == history.versionTagId }
                if (tag != null && (tag.name != history.versionTagName || tag.typeCode != history.versionTagTypeCode)) {
                    issues.add(
                        MigrationStatusReport.Issue.Code.INCONSISTENT_TAG_ID,
                        "Resolved Version Tag ${history.versionTagId} metadata conflicts with the Tag row",
                        affectedVersionNames = listOfNotNull(expectedVersion, actualVersion),
                        affectedMigrationIds = listOfNotNull(history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }),
                        tagIds = listOf(history.tagId, history.versionTagId),
                        versionIds = listOfNotNull(history.resolvedVersionId),
                        historyRecordIds = listOf(history.id),
                        versionReferenceIds = listOfNotNull(history.versionReferenceId),
                    )
                }
            }

            if (expectedVersion != null && (
                    history.versionTagName != expectedVersion ||
                    history.versionTagTypeCode != MigrationTagType.VERSION.code ||
                    history.resolvedVersionId != history.versionReferenceId
                )
            ) {
                issues.add(
                    MigrationStatusReport.Issue.Code.WRONG_VERSION_ASSOCIATION,
                    "Configured Migration '${history.tagName}' is recorded outside Version '$expectedVersion'",
                    affectedVersionNames = listOfNotNull(expectedVersion, actualVersion),
                    affectedMigrationIds = listOf(history.tagName),
                    tagIds = listOfNotNull(history.tagId, history.versionTagId),
                    versionIds = listOfNotNull(history.resolvedVersionId),
                    historyRecordIds = listOf(history.id),
                    versionReferenceIds = listOfNotNull(history.versionReferenceId),
                )
            }
        }

        facts.histories.groupBy(HistoryFact::tagId).forEach { (tagId, rows) ->
            val recordIds = rows.map(HistoryFact::id).distinct()
            if (recordIds.size > 1) {
                val itemName = rows.first().tagName.takeIf { rows.first().tagTypeCode == MigrationTagType.ITEM.code }
                val actualVersionNames = rows.mapNotNull { row ->
                    row.versionTagName.takeIf { row.versionTagTypeCode == MigrationTagType.VERSION.code }
                }
                val configuredOwnerNames = rows.mapNotNull { row ->
                    migrationOwners[row.tagName].takeIf { row.tagTypeCode == MigrationTagType.ITEM.code }
                }
                val versionReferences = rows.mapNotNull(HistoryFact::versionReferenceId).distinct()
                issues.add(
                    MigrationStatusReport.Issue.Code.DUPLICATE_HISTORY_RECORD,
                    "Migration Tag $tagId has multiple applied history records $recordIds " +
                        "across Version references $versionReferences",
                    affectedVersionNames = (configuredOwnerNames + actualVersionNames).distinct(),
                    affectedMigrationIds = listOfNotNull(itemName),
                    tagIds = listOf(tagId),
                    versionIds = rows.mapNotNull(HistoryFact::resolvedVersionId),
                    historyRecordIds = recordIds,
                    versionReferenceIds = versionReferences,
                )
            }
        }
    }

    private fun validateVersionOrder(
        configuration: MigrationConfiguration,
        facts: HistoryFacts,
        issues: IssueCollector,
    ) {
        val configuredNames = configuration.registrations.mapTo(hashSetOf()) { it.name }
        // Foreign Versions are reported separately and must not shift configured chronology.
        facts.versions
            .filter { it.tagTypeCode == MigrationTagType.VERSION.code && it.tagName in configuredNames }
            .sortedBy(VersionFact::id)
            .forEachIndexed { index, persisted ->
            val expectedName = configuration.registrations.getOrNull(index)?.name
            val actualName = persisted.tagName
            if (expectedName != actualName) {
                issues.add(
                    MigrationStatusReport.Issue.Code.VERSION_ORDER_MISMATCH,
                    "Persisted Version id ${persisted.id} has Tag '${actualName ?: "<missing>"}'; " +
                        "Configuration position $index expects '${expectedName ?: "no Version"}'",
                    affectedVersionNames = listNotNull(expectedName, actualName),
                    tagIds = listOfNotNull(persisted.tagId),
                    versionIds = listOf(persisted.id),
                )
            }
        }
    }

    private fun validateUnexpectedHistory(
        preparedVersions: List<PreparedMigrationVersion>,
        facts: HistoryFacts,
        migrationOwners: Map<String, String>,
        issues: IssueCollector,
    ) {
        val configuredVersionIds = preparedVersions.associate { prepared ->
            prepared.version.name to facts.versions.filter {
                it.tagName == prepared.version.name && it.tagTypeCode == MigrationTagType.VERSION.code
            }.map(VersionFact::id).distinct()
        }
        preparedVersions.forEach { prepared ->
            val versionName = prepared.version.name
            val versionIds = configuredVersionIds[versionName].orEmpty()
            val expectedMigrationIds = prepared.migrations.mapTo(linkedSetOf()) { it.id.value }
            facts.histories.filter { it.versionReferenceId in versionIds }.forEach { history ->
                val itemName = history.tagName.takeIf { history.tagTypeCode == MigrationTagType.ITEM.code }
                if (itemName == null || itemName !in expectedMigrationIds) {
                    val knownOwner = itemName?.let(migrationOwners::get)
                    issues.add(
                        MigrationStatusReport.Issue.Code.UNEXPECTED_HISTORY_FOR_VERSION,
                        "Version '$versionName' contains unexpected Migration history ${history.id} for '${history.tagName}'",
                        affectedVersionNames = listNotNull(versionName, knownOwner),
                        affectedMigrationIds = listOfNotNull(itemName),
                        tagIds = listOf(history.tagId),
                        versionIds = listOfNotNull(history.resolvedVersionId),
                        historyRecordIds = listOf(history.id),
                        versionReferenceIds = listOfNotNull(history.versionReferenceId),
                    )
                }
            }
        }
    }

    private fun validateMissingExpectedHistory(
        preparedVersions: List<PreparedMigrationVersion>,
        facts: HistoryFacts,
        migrationOwners: Map<String, String>,
        issues: IssueCollector,
    ) {
        preparedVersions.forEach { prepared ->
            val versionName = prepared.version.name
            val versionTagIds = facts.tags.filter {
                it.typeCode == MigrationTagType.VERSION.code && it.name == versionName
            }.map(TagFact::id).toSet()
            val versionIds = facts.versions.filter {
                it.tagId in versionTagIds && it.tagName == versionName
            }.map(VersionFact::id).distinct()
            if (versionIds.isEmpty()) return@forEach

            prepared.migrations.forEach { migration ->
                val migrationId = migration.id.value
                val itemTagIds = facts.tags.filter {
                    it.typeCode == MigrationTagType.ITEM.code && it.name == migrationId
                }.map(TagFact::id).toSet()
                val history = facts.histories.filter { it.tagId in itemTagIds }
                if (history.isEmpty()) {
                    issues.add(
                        MigrationStatusReport.Issue.Code.MISSING_EXPECTED_MIGRATION_HISTORY,
                        "Applied Version '$versionName' has no history record for configured Migration '$migrationId'",
                        affectedVersionNames = listOf(versionName),
                        affectedMigrationIds = listOf(migrationId),
                        tagIds = itemTagIds.toList(),
                        versionIds = versionIds,
                    )
                    issues.add(
                        MigrationStatusReport.Issue.Code.INCOMPLETE_VERSION_HISTORY,
                        "Applied Version '$versionName' does not contain all configured Migration history",
                        affectedVersionNames = listOf(versionName),
                        affectedMigrationIds = listOf(migrationId),
                        tagIds = itemTagIds.toList(),
                        versionIds = versionIds,
                    )
                }
            }
        }
    }

    private fun configuredMigrationEntry(
        registered: RegisteredMigration,
        ownerVersionName: String,
        ownerVersionIds: List<Int>,
        facts: HistoryFacts,
        issues: IssueCollector,
    ): MigrationStatusReport.ConfiguredMigration {
        val migrationId = registered.id.value
        val tags = facts.tags.filter {
            it.typeCode == MigrationTagType.ITEM.code && it.name == migrationId
        }
        val tagIds = tags.map(TagFact::id).distinct()
        val history = facts.histories.filter { it.tagId in tagIds }
        val historyIds = history.map(HistoryFact::id).distinct()
        val referenceIds = history.mapNotNull(HistoryFact::versionReferenceId).distinct()
        val itemIssues = issues.forMigration(migrationId)

        val structurallyValidApplication = history.size == 1 && tagIds.size == 1 && ownerVersionIds.size == 1 &&
            history.singleOrNull()?.let { row ->
                row.tagTypeCode == MigrationTagType.ITEM.code &&
                    row.versionReferenceId == ownerVersionIds.single() &&
                    row.resolvedVersionId == ownerVersionIds.single() &&
                    row.versionTagName == ownerVersionName &&
                    row.versionTagTypeCode == MigrationTagType.VERSION.code
            } == true

        val status = when {
            itemIssues.isNotEmpty() -> MigrationItemStatus.INVALID
            history.isEmpty() && ownerVersionIds.isEmpty() -> MigrationItemStatus.PENDING
            history.isEmpty() -> MigrationItemStatus.INVALID
            structurallyValidApplication -> MigrationItemStatus.APPLIED
            else -> MigrationItemStatus.INVALID
        }
        return MigrationStatusReport.ConfiguredMigration(
            id = migrationId,
            status = status,
            tagIds = tagIds,
            historyRecordIds = historyIds,
            versionReferenceIds = referenceIds,
            issues = itemIssues,
        )
    }

    private fun buildUnknownVersions(
        facts: HistoryFacts,
        configuredNames: Set<String>,
        issues: IssueCollector,
    ): List<MigrationStatusReport.UnknownVersion> {
        val tagNames = facts.tags.filter {
            it.typeCode == MigrationTagType.VERSION.code && it.name !in configuredNames
        }.map(TagFact::name)
        val versionNames = facts.versions.filter {
            it.tagTypeCode == MigrationTagType.VERSION.code && it.tagName != null && it.tagName !in configuredNames
        }.mapNotNull(VersionFact::tagName)
        val names = (tagNames + versionNames).distinct()
        val result = names.map { name ->
            val tags = facts.tags.filter { it.typeCode == MigrationTagType.VERSION.code && it.name == name }
            val versionFacts = facts.versions.filter {
                it.tagName == name && it.tagTypeCode == MigrationTagType.VERSION.code
            }
            val tagIds = (tags.map(TagFact::id) + versionFacts.mapNotNull(VersionFact::tagId)).distinct()
            val versionIds = versionFacts.map(VersionFact::id).distinct()
            val histories = facts.histories.filter {
                it.versionReferenceId in versionIds || it.versionTagName == name
            }
            val versionIssues = issues.forVersion(name)
            MigrationStatusReport.UnknownVersion(
                name = name,
                status = if (hasStructuralIssues(versionIssues)) MigrationVersionStatus.INVALID else MigrationVersionStatus.UNKNOWN,
                tagIds = tagIds,
                versionIds = versionIds,
                historyRecordIds = histories.map(HistoryFact::id).distinct(),
                issues = versionIssues,
            )
        }.toMutableList()

        facts.versions.filter { it.tagName == null || it.tagTypeCode == null }.forEach { version ->
            val versionIssues = issues.forVersion(
                name = null,
                persistedVersionId = version.id,
                persistedTagId = version.tagId,
            )
            result += MigrationStatusReport.UnknownVersion(
                name = null,
                status = MigrationVersionStatus.INVALID,
                tagIds = listOfNotNull(version.tagId),
                versionIds = listOf(version.id),
                historyRecordIds = facts.histories.filter { it.versionReferenceId == version.id }
                    .map(HistoryFact::id).distinct(),
                issues = versionIssues,
            )
        }
        return result
    }

    private fun buildUnknownMigrations(
        facts: HistoryFacts,
        configuredMigrations: Map<String, String>,
        issues: IssueCollector,
    ): List<MigrationStatusReport.UnknownMigration> {
        val names = facts.tags.filter { it.typeCode == MigrationTagType.ITEM.code && it.name !in configuredMigrations }
            .map(TagFact::name)
            .distinct()
        return names.map { name ->
            val tags = facts.tags.filter { it.typeCode == MigrationTagType.ITEM.code && it.name == name }
            val tagIds = tags.map(TagFact::id).distinct()
            val histories = facts.histories.filter { it.tagId in tagIds }
            val itemIssues = issues.forMigration(name)
            val invalidIdentity = try {
                MigrationIdentity.fromValue(name)
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            MigrationStatusReport.UnknownMigration(
                id = name,
                status = if (invalidIdentity || hasStructuralIssues(itemIssues)) {
                    MigrationItemStatus.INVALID
                } else {
                    MigrationItemStatus.UNKNOWN
                },
                tagIds = tagIds,
                historyRecordIds = histories.map(HistoryFact::id).distinct(),
                versionReferenceIds = histories.mapNotNull(HistoryFact::versionReferenceId).distinct(),
                issues = itemIssues,
            )
        }
    }

    private fun buildUnknownTags(
        facts: HistoryFacts,
        issues: IssueCollector,
    ): List<MigrationStatusReport.UnknownTag> = facts.tags
        .filter { it.typeCode != MigrationTagType.VERSION.code && it.typeCode != MigrationTagType.ITEM.code }
        .map { tag ->
            val versions = facts.versions.filter { it.tagId == tag.id }
            val histories = facts.histories.filter { it.tagId == tag.id }
            MigrationStatusReport.UnknownTag(
                tagId = tag.id,
                name = tag.name,
                typeCode = tag.typeCode,
                versionIds = versions.map(VersionFact::id).distinct(),
                historyRecordIds = histories.map(HistoryFact::id).distinct(),
                versionReferenceIds = histories.mapNotNull(HistoryFact::versionReferenceId).distinct(),
                issues = issues.forTag(tag.id),
            )
        }

    private fun hasStructuralIssues(issues: List<MigrationStatusReport.Issue>): Boolean =
        issues.any { it.code !in NON_STRUCTURAL_ISSUES }

    private data class TagFact(val id: Int, val name: String, val typeCode: Int)

    private data class VersionFact(
        val id: Int,
        val tagId: Int?,
        val tagName: String?,
        val tagTypeCode: Int?,
    )

    private data class HistoryFact(
        val id: Int,
        val tagId: Int,
        val tagName: String,
        val tagTypeCode: Int,
        val versionReferenceId: Int?,
        val resolvedVersionId: Int?,
        val versionTagId: Int?,
        val versionTagName: String?,
        val versionTagTypeCode: Int?,
    )

    private data class HistoryFacts(
        val tags: List<TagFact>,
        val versions: List<VersionFact>,
        val histories: List<HistoryFact>,
    )

    private class IssueCollector {
        private val items = linkedMapOf<IssueDraft, IssueDraft>()

        fun add(
            code: MigrationStatusReport.Issue.Code,
            message: String,
            affectedVersionNames: List<String> = emptyList(),
            affectedMigrationIds: List<String> = emptyList(),
            tagIds: List<Int> = emptyList(),
            versionIds: List<Int> = emptyList(),
            historyRecordIds: List<Int> = emptyList(),
            versionReferenceIds: List<Int> = emptyList(),
        ) {
            val draft = IssueDraft(
                code = code,
                message = message,
                affectedVersionNames = affectedVersionNames.distinct(),
                affectedMigrationIds = affectedMigrationIds.distinct(),
                tagIds = tagIds.distinct(),
                versionIds = versionIds.distinct(),
                historyRecordIds = historyRecordIds.distinct(),
                versionReferenceIds = versionReferenceIds.distinct(),
            )
            items.putIfAbsent(draft, draft)
        }

        fun values(): List<MigrationStatusReport.Issue> = items.values.map { it.toModel() }

        fun forVersion(
            name: String?,
            persistedVersionId: Int? = null,
            persistedTagId: Int? = null,
        ): List<MigrationStatusReport.Issue> = values().filter { issue ->
            if (name != null) {
                name in issue.affectedVersionNames
            } else {
                (persistedVersionId != null && persistedVersionId in issue.versionIds) ||
                    (persistedTagId != null && persistedTagId in issue.tagIds)
            }
        }

        fun forMigration(id: String): List<MigrationStatusReport.Issue> = values().filter {
            id in it.affectedMigrationIds
        }

        fun forTag(id: Int): List<MigrationStatusReport.Issue> = values().filter { id in it.tagIds }
    }

    private data class IssueDraft(
        val code: MigrationStatusReport.Issue.Code,
        val message: String,
        val affectedVersionNames: List<String>,
        val affectedMigrationIds: List<String>,
        val tagIds: List<Int>,
        val versionIds: List<Int>,
        val historyRecordIds: List<Int>,
        val versionReferenceIds: List<Int>,
    ) {
        fun toModel() = MigrationStatusReport.Issue(
            code = code,
            message = message,
            affectedVersionNames = affectedVersionNames,
            affectedMigrationIds = affectedMigrationIds,
            tagIds = tagIds,
            versionIds = versionIds,
            historyRecordIds = historyRecordIds,
            versionReferenceIds = versionReferenceIds,
        )
    }

    private companion object {
        val NON_STRUCTURAL_ISSUES = setOf(
            MigrationStatusReport.Issue.Code.UNKNOWN_PERSISTED_VERSION,
            MigrationStatusReport.Issue.Code.UNKNOWN_PERSISTED_MIGRATION,
            MigrationStatusReport.Issue.Code.UNKNOWN_PERSISTED_TAG,
        )
    }
}

private fun listNotNull(vararg values: String?): List<String> = values.filterNotNull().distinct()
