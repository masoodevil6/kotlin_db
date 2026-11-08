package gog.my_project.data_base_migration.executor.service.status

import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import model.migration_status.MigrationItemStatus
import model.migration_status.MigrationStatusReport
import model.migration_status.MigrationStatusRow
import model.migration_status.MigrationStatusSnapshot
import model.migration_status.MigrationVersionStatus
import model.prepared.PreparedMigrationVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationStatusAnalyzerTest {
    private val analyzer = MigrationStatusAnalyzer()

    @Test
    fun `empty configuration and history produce an empty report`() {
        val report = analyzer.analyze(MigrationConfiguration(emptyList()), MigrationStatusSnapshot(emptyList()))

        assertEquals(emptyList(), report.versions)
        assertEquals(emptyList(), report.unknownVersions)
        assertEquals(emptyList(), report.unknownMigrations)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun `unapplied configuration is pending`() {
        val report = analyzer.analyze(configuration(version("v1", "first_migration")), MigrationStatusSnapshot(emptyList()))

        assertEquals(MigrationVersionStatus.PENDING, report.versions.single().status)
        assertEquals(MigrationItemStatus.PENDING, report.versions.single().migrations.single().status)
    }

    @Test
    fun `complete persisted history is applied`() {
        val config = configuration(version("v1", "first_migration"))
        val report = analyzer.analyze(config, snapshot(
            versionRow(1, "v1", 100),
            migrationRow(2, "first_migration", 200, 100, 100, "v1"),
        ))

        assertEquals(MigrationVersionStatus.APPLIED, report.versions.single().status, describeIssues(report))
        assertEquals(MigrationItemStatus.APPLIED, report.versions.single().migrations.single().status)
        assertEquals(listOf(100), report.versions.single().versionIds)
        assertEquals(listOf(200), report.versions.single().migrations.single().historyRecordIds)
    }

    @Test
    fun `all configured versions and migrations can be applied`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration"), version("v2", "second_migration")),
            snapshot(
                versionRow(1, "v1", 10),
                migrationRow(2, "first_migration", 20, 10, 10, "v1", resolvedVersionTagId = 1),
                versionRow(3, "v2", 30),
                migrationRow(4, "second_migration", 40, 30, 30, "v2", resolvedVersionTagId = 3),
            ),
        )

        assertEquals(listOf(MigrationVersionStatus.APPLIED, MigrationVersionStatus.APPLIED), report.versions.map { it.status })
        assertEquals(listOf(MigrationItemStatus.APPLIED, MigrationItemStatus.APPLIED), report.versions.map { it.migrations.single().status })
    }

    @Test
    fun `partially applied configuration retains declaration order and pending state`() {
        val config = configuration(version("v2", "second_migration"), version("v10", "tenth_migration"))
        val report = analyzer.analyze(
            config,
            snapshot(
                versionRow(1, "v2", 100),
                migrationRow(2, "second_migration", 200, 100, 100, "v2"),
            ),
        )

        assertEquals(listOf("v2", "v10"), report.versions.map { it.name })
        assertEquals(
            listOf(MigrationVersionStatus.APPLIED, MigrationVersionStatus.PENDING),
            report.versions.map { it.status },
            describeIssues(report),
        )
        assertEquals(MigrationItemStatus.PENDING, report.versions[1].migrations.single().status)
    }

    @Test
    fun `unknown persisted version is retained in report`() {
        val report = analyzer.analyze(
            configuration(version("v1")),
            snapshot(versionRow(99, "legacy_v", 999)),
        )

        val unknown = report.unknownVersions.single()
        assertEquals("legacy_v", unknown.name)
        assertEquals(MigrationVersionStatus.UNKNOWN, unknown.status)
        assertEquals(listOf(99), unknown.tagIds)
        assertEquals(listOf(999), unknown.versionIds)
    }

    @Test
    fun `unknown persisted item tag is represented instead of becoming configured pending`() {
        val report = analyzer.analyze(
            configuration(version("v1")),
            snapshot(versionRow(1, "v1", 10), itemTagRow(2, "removed_migration")),
        )

        val unknown = report.unknownMigrations.single()
        assertEquals("removed_migration", unknown.id)
        assertEquals(MigrationItemStatus.UNKNOWN, unknown.status)
        assertEquals(listOf(2), unknown.tagIds)
    }

    @Test
    fun `unknown migration history record remains visible under an unknown persisted version`() {
        val report = analyzer.analyze(
            configuration(version("configured_v")),
            snapshot(
                versionRow(99, "retired_v", 999),
                migrationRow(
                    tagId = 100,
                    name = "removed_migration",
                    historyId = 777,
                    versionReferenceId = 999,
                    resolvedVersionId = 999,
                    resolvedVersionName = "retired_v",
                    resolvedVersionTagId = 99,
                ),
            ),
        )

        val unknownVersion = report.unknownVersions.single()
        val unknownMigration = report.unknownMigrations.single()
        assertEquals(MigrationVersionStatus.UNKNOWN, unknownVersion.status)
        assertEquals(MigrationItemStatus.UNKNOWN, unknownMigration.status)
        assertEquals(listOf(777), unknownVersion.historyRecordIds)
        assertEquals(listOf(777), unknownMigration.historyRecordIds)
    }

    @Test
    fun `malformed unknown version is invalid rather than merely unknown`() {
        val report = analyzer.analyze(
            configuration(version("configured_v")),
            snapshot(versionRow(99, "retired_v", -999)),
        )

        assertEquals(MigrationVersionStatus.INVALID, report.unknownVersions.single().status)
        assertTrueIssue(report, MigrationStatusReport.Issue.Code.INVALID_PERSISTED_ID)
    }

    @Test
    fun `missing expected history in a persisted version is invalid`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration")),
            snapshot(versionRow(1, "v1", 10)),
        )

        assertEquals(MigrationVersionStatus.INVALID, report.versions.single().status)
        assertEquals(MigrationItemStatus.INVALID, report.versions.single().migrations.single().status)
        assertTrueIssue(report, MigrationStatusReport.Issue.Code.MISSING_EXPECTED_MIGRATION_HISTORY)
    }

    @Test
    fun `duplicate version records are retained and invalidate the configured version`() {
        val report = analyzer.analyze(
            configuration(version("v1")),
            snapshot(versionRow(1, "v1", 10), versionRow(1, "v1", 11)),
        )

        assertEquals(MigrationVersionStatus.INVALID, report.versions.single().status)
        assertEquals(listOf(10, 11), report.versions.single().versionIds)
        assertTrueIssue(report, MigrationStatusReport.Issue.Code.DUPLICATE_VERSION_RECORD)
    }

    @Test
    fun `duplicate tags with the same persisted identity are reported`() {
        val report = analyzer.analyze(
            configuration(version("v1")),
            snapshot(versionRow(1, "v1", 10), versionRow(2, "v1", 11)),
        )

        assertTrueIssue(report, MigrationStatusReport.Issue.Code.DUPLICATE_TAG)
        assertEquals(MigrationVersionStatus.INVALID, report.versions.single().status)
        assertEquals(listOf(1, 2), report.versions.single().versionTagIds)
    }

    @Test
    fun `duplicate migration history records are retained and invalidate the item`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration")),
            snapshot(
                versionRow(1, "v1", 10),
                migrationRow(2, "first_migration", 20, 10, 1, "v1"),
                migrationRow(2, "first_migration", 21, 10, 1, "v1"),
            ),
        )

        assertEquals(MigrationItemStatus.INVALID, report.versions.single().migrations.single().status)
        assertEquals(listOf(20, 21), report.versions.single().migrations.single().historyRecordIds)
        assertTrueIssue(report, MigrationStatusReport.Issue.Code.DUPLICATE_HISTORY_RECORD)
        val duplicateIssues = report.issues.filter { it.code == MigrationStatusReport.Issue.Code.DUPLICATE_HISTORY_RECORD }
        assertEquals(1, duplicateIssues.size)
        assertEquals(listOf(20, 21), duplicateIssues.single().historyRecordIds)
        assertEquals(listOf(10), duplicateIssues.single().versionReferenceIds)
    }

    @Test
    fun `duplicate migration history across different versions is reported once with complete evidence`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration"), version("v2", "second_migration")),
            snapshot(
                versionRow(1, "v1", 10),
                versionRow(3, "v2", 20),
                migrationRow(2, "first_migration", 30, 10, 10, "v1", resolvedVersionTagId = 1),
                migrationRow(2, "first_migration", 31, 20, 20, "v2", resolvedVersionTagId = 3),
            ),
        )

        val duplicateIssue = report.issues.single {
            it.code == MigrationStatusReport.Issue.Code.DUPLICATE_HISTORY_RECORD
        }
        assertEquals(listOf(30, 31), duplicateIssue.historyRecordIds)
        assertEquals(listOf(10, 20), duplicateIssue.versionIds)
        assertEquals(listOf(10, 20), duplicateIssue.versionReferenceIds)
        assertEquals(listOf("v1", "v2"), duplicateIssue.affectedVersionNames)
    }

    @Test
    fun `migration associated with an incorrect version is invalid`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration"), version("v2", "second_migration")),
            snapshot(
                versionRow(1, "v1", 10),
                versionRow(3, "v2", 20),
                migrationRow(2, "first_migration", 30, 20, 20, "v2", resolvedVersionTagId = 3),
            ),
        )

        assertEquals(MigrationItemStatus.INVALID, report.versions[0].migrations.single().status)
        assertTrueIssue(report, MigrationStatusReport.Issue.Code.WRONG_VERSION_ASSOCIATION)
        assertEquals(listOf(30), report.versions[0].migrations.single().historyRecordIds)
        assertEquals(listOf(20), report.versions[0].migrations.single().versionReferenceIds)
    }

    @Test
    fun `broken version reference is invalid and retains raw reference`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration")),
            snapshot(
                versionRow(1, "v1", 10),
                migrationRow(2, "first_migration", 20, 999, null, null),
            ),
        )

        assertEquals(MigrationVersionStatus.INVALID, report.versions.single().status)
        assertEquals(MigrationItemStatus.INVALID, report.versions.single().migrations.single().status)
        assertEquals(listOf(999), report.versions.single().migrations.single().versionReferenceIds)
        assertTrueIssue(report, MigrationStatusReport.Issue.Code.BROKEN_VERSION_REFERENCE)
    }

    @Test
    fun `invalid tag type cannot make configured migration appear pending`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration")),
            snapshot(
                versionRow(1, "v1", 10),
                itemTagRow(2, "first_migration", type = 77),
            ),
        )

        assertEquals(MigrationItemStatus.INVALID, report.versions.single().migrations.single().status)
        assertTrueIssue(report, MigrationStatusReport.Issue.Code.INVALID_TAG_TYPE)
    }

    @Test
    fun `unsupported tag types cannot leave configured version or migration pending`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration")),
            snapshot(
                itemTagRow(1, "v1", type = 77),
                itemTagRow(2, "first_migration", type = 77),
            ),
        )

        val configuredVersion = report.versions.single()
        assertEquals(MigrationVersionStatus.INVALID, configuredVersion.status)
        assertEquals(MigrationItemStatus.INVALID, configuredVersion.migrations.single().status)
        val versionTypeIssue = report.issues.single {
            it.code == MigrationStatusReport.Issue.Code.INVALID_TAG_TYPE && 1 in it.tagIds
        }
        val migrationTypeIssue = report.issues.single {
            it.code == MigrationStatusReport.Issue.Code.INVALID_TAG_TYPE && 2 in it.tagIds
        }
        assertEquals(listOf("v1"), versionTypeIssue.affectedVersionNames)
        assertEquals(listOf("v1"), migrationTypeIssue.affectedVersionNames)
        assertEquals(listOf("first_migration"), migrationTypeIssue.affectedMigrationIds)
    }

    @Test
    fun `version attached to unsupported tag type invalidates the configured version`() {
        val report = analyzer.analyze(
            configuration(version("v1")),
            snapshot(versionRow(1, "v1", 10, type = 77)),
        )

        assertEquals(MigrationVersionStatus.INVALID, report.versions.single().status)
        val associationIssue = report.issues.single {
            it.code == MigrationStatusReport.Issue.Code.VERSION_ATTACHED_TO_INVALID_TAG_TYPE
        }
        assertEquals(listOf("v1"), associationIssue.affectedVersionNames)
        assertEquals(listOf(10), associationIssue.versionIds)
    }

    @Test
    fun `version with incomplete tag metadata is attributed by its configured tag name`() {
        val incompleteRow = MigrationStatusRow(
            tagId = 2,
            tagName = "first_migration",
            tagType = ITEM_TYPE,
            tagVersionId = null,
            migrationHistoryId = 20,
            appliedVersionReferenceId = 10,
            appliedVersionId = 10,
            appliedVersionTagId = null,
            appliedVersionTagName = "v1",
            appliedVersionTagType = null,
        )
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration")),
            snapshot(incompleteRow),
        )

        assertEquals(MigrationVersionStatus.INVALID, report.versions.single().status)
        val metadataIssues = report.issues.filter {
            it.code == MigrationStatusReport.Issue.Code.BROKEN_VERSION_TAG_REFERENCE && 10 in it.versionIds
        }
        assertTrue(metadataIssues.isNotEmpty())
        assertTrue(metadataIssues.all { "v1" in it.affectedVersionNames })
    }

    @Test
    fun `unnamed unknown versions receive only issues linked by their persisted identifiers`() {
        val report = analyzer.analyze(
            configuration(version("configured_v")),
            snapshot(
                migrationRow(1, "old_first", 10, 99, 99, null, resolvedVersionTagId = 60),
                migrationRow(2, "old_second", 11, 100, 100, null, resolvedVersionTagId = null),
                itemTagRow(3, "unrelated_bad_tag", type = 77),
                itemTagRow(60, "orphan_bad_tag", type = 77),
            ),
        )

        val unnamedVersions = report.unknownVersions.sortedBy { it.versionIds.single() }
        assertEquals(listOf(99, 100), unnamedVersions.map { it.versionIds.single() })
        unnamedVersions.forEach { unknown ->
            assertTrue(unknown.issues.isNotEmpty())
            assertTrue(unknown.issues.all {
                unknown.versionIds.single() in it.versionIds || unknown.tagIds.any(it.tagIds::contains)
            })
            assertTrue(unknown.issues.none { 3 in it.tagIds })
        }
        assertTrue(unnamedVersions[0].issues.any {
            it.code == MigrationStatusReport.Issue.Code.INVALID_TAG_TYPE && 60 in it.tagIds
        })
        assertTrue(unnamedVersions[1].issues.none { 60 in it.tagIds })
    }

    @Test
    fun `reports multiple independent inconsistencies from one snapshot`() {
        val report = analyzer.analyze(
            configuration(version("v1", "first_migration")),
            snapshot(
                versionRow(1, "v1", 10),
                migrationRow(2, "first_migration", -20, 999, null, null, type = 88),
            ),
        )

        val codes = report.issues.map { it.code }.toSet()
        kotlin.test.assertTrue(MigrationStatusReport.Issue.Code.INVALID_PERSISTED_ID in codes)
        kotlin.test.assertTrue(MigrationStatusReport.Issue.Code.INVALID_TAG_TYPE in codes)
        kotlin.test.assertTrue(MigrationStatusReport.Issue.Code.BROKEN_VERSION_REFERENCE in codes)
        assertEquals(MigrationVersionStatus.INVALID, report.versions.single().status)
    }

    @Test
    fun `configuration order and migration declaration order are not sorted by identity`() {
        val config = configuration(
            version("v10", "z_migration", "a_migration"),
            version("v2", "b_migration"),
        )
        val report = analyzer.analyze(config, MigrationStatusSnapshot(emptyList()))

        assertEquals(listOf("v10", "v2"), report.versions.map { it.name })
        assertEquals(listOf("z_migration", "a_migration"), report.versions[0].migrations.map { it.id })
    }

    @Test
    fun `analysis does not mutate configuration or snapshot inputs`() {
        val sourceVersions = mutableListOf(version("v1", "first_migration"))
        val config = MigrationConfiguration(sourceVersions)
        val sourceRows = mutableListOf(versionRow(1, "foreign_v", 99))
        val snapshot = MigrationStatusSnapshot(sourceRows)
        sourceVersions.clear()
        sourceRows.clear()

        val report = analyzer.analyze(config, snapshot)

        assertEquals(listOf("v1"), config.registrations.map { it.name })
        assertEquals(1, snapshot.rows.size)
        assertEquals(listOf("v1"), report.versions.map { it.name })
        assertEquals("foreign_v", report.unknownVersions.single().name)
    }

    @Test
    fun `fatal Error from preparation propagates unchanged`() {
        val fatal = AssertionError("fatal analyzer failure")
        val configured = configuration(version("v1"))
        val service = MigrationStatusAnalyzer { throw fatal }

        val thrown = assertFailsWith<AssertionError> {
            service.analyze(configured, MigrationStatusSnapshot(emptyList()))
        }

        assertSame(fatal, thrown)
    }

    private fun configuration(vararg versions: MigrationVersion) = MigrationConfiguration(versions.toList())

    private fun version(name: String, vararg migrationIds: String) = MigrationVersion(
        name,
        migrationIds.map { id -> SingleMigration(registered(id)) },
    )

    private fun registered(id: String) = RegisteredMigration(MigrationIdentity.fromValue(id), NoopMigration::class)

    private fun snapshot(vararg rows: MigrationStatusRow) = MigrationStatusSnapshot(rows.toList())

    private fun versionRow(tagId: Int, name: String, versionId: Int, type: Int = VERSION_TYPE) = MigrationStatusRow(
        tagId = tagId,
        tagName = name,
        tagType = type,
        tagVersionId = versionId,
        migrationHistoryId = null,
        appliedVersionReferenceId = null,
        appliedVersionId = null,
        appliedVersionTagId = null,
        appliedVersionTagName = null,
        appliedVersionTagType = null,
    )

    private fun itemTagRow(tagId: Int, name: String, type: Int = ITEM_TYPE) = MigrationStatusRow(
        tagId = tagId,
        tagName = name,
        tagType = type,
        tagVersionId = null,
        migrationHistoryId = null,
        appliedVersionReferenceId = null,
        appliedVersionId = null,
        appliedVersionTagId = null,
        appliedVersionTagName = null,
        appliedVersionTagType = null,
    )

    private fun migrationRow(
        tagId: Int,
        name: String,
        historyId: Int,
        versionReferenceId: Int,
        resolvedVersionId: Int?,
        resolvedVersionName: String?,
        type: Int = ITEM_TYPE,
        resolvedVersionTagId: Int? = resolvedVersionId?.let { 1 },
    ) = MigrationStatusRow(
        tagId = tagId,
        tagName = name,
        tagType = type,
        tagVersionId = null,
        migrationHistoryId = historyId,
        appliedVersionReferenceId = versionReferenceId,
        appliedVersionId = resolvedVersionId,
        appliedVersionTagId = resolvedVersionTagId,
        appliedVersionTagName = resolvedVersionName,
        appliedVersionTagType = resolvedVersionName?.let { VERSION_TYPE },
    )

    private fun assertTrueIssue(report: MigrationStatusReport, expected: MigrationStatusReport.Issue.Code) {
        kotlin.test.assertTrue(report.issues.any { it.code == expected }, "Expected issue $expected; got ${report.issues.map { it.code }}")
    }

    private fun describeIssues(report: MigrationStatusReport) = report.issues.joinToString { "${it.code}: ${it.message}" }

    private enum class ItemTags(override val value: String) : MigrationItemTag {
        NOOP("noop_migration"),
    }

    private class NoopMigration : Migration(ItemTags.NOOP) {
        override fun up(): MigrationDefinition = error("Analyzer must not execute migrations")
        override fun down(): MigrationDefinition = error("Analyzer must not execute migrations")
    }

    private companion object {
        const val VERSION_TYPE = 0
        const val ITEM_TYPE = 1
    }
}
