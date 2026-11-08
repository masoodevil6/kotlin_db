package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.MigrationGroup
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelection
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelectionDirection
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.version_history.MigrationHistoryEntry
import model.version_history.MigrationHistoryTag
import model.version_history.MigrationHistoryVersion
import model.version_history.MigrationVersionHistorySnapshot
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService
import kotlin.reflect.KClass
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationVersionTraversalServiceTest {
    private val configuration = createConfiguration()
    private val traversalService = MigrationVersionTraversalService()
    private val historyValidator = MigrationVersionHistoryValidator()

    @BeforeTest
    fun resetMigrationCounters() {
        MigrationProbe.constructorCalls = 0
        MigrationProbe.upCalls = 0
        MigrationProbe.downCalls = 0
    }

    @Test
    fun upSelectionMapsToUpPlanWithoutChangingOrder() {
        val versions = listOf(traversalVersion("v3"), traversalVersion("v4"))
        val plan = traversalService.plan(
            MigrationVersionSelection(MigrationVersionSelectionDirection.UP, versions),
        )

        assertEquals(MigrationVersionTraversalDirection.UP, plan.direction)
        assertEquals(listOf("v3", "v4"), plan.versions.map { it.version.name })
        assertSame(versions[0], plan.versions[0])
        assertSame(versions[1], plan.versions[1])
    }

    @Test
    fun downSelectionMapsToDownPlanWithoutChangingDescendingOrder() {
        val versions = listOf(traversalVersion("v4"), traversalVersion("v3"), traversalVersion("v2"))
        val plan = traversalService.plan(
            MigrationVersionSelection(MigrationVersionSelectionDirection.DOWN, versions),
        )

        assertEquals(MigrationVersionTraversalDirection.DOWN, plan.direction)
        assertEquals(listOf("v4", "v3", "v2"), plan.versions.map { it.version.name })
        versions.zip(plan.versions).forEach { (expected, actual) -> assertSame(expected, actual) }
    }

    @Test
    fun emptySelectionsProduceEmptyPlansAndRetainTheirDirection() {
        val up = traversalService.plan(MigrationVersionSelection(MigrationVersionSelectionDirection.UP, emptyList()))
        val down = traversalService.plan(MigrationVersionSelection(MigrationVersionSelectionDirection.DOWN, emptyList()))

        assertEquals(MigrationVersionTraversalDirection.UP, up.direction)
        assertTrue(up.versions.isEmpty())
        assertEquals(MigrationVersionTraversalDirection.DOWN, down.direction)
        assertTrue(down.versions.isEmpty())
    }

    @Test
    fun traversalPlanIsImmutable() {
        val plan = traversalService.plan(
            MigrationVersionSelection(
                MigrationVersionSelectionDirection.UP,
                listOf(traversalVersion("v1")),
            ),
        )

        assertFailsWith<UnsupportedOperationException> { (plan.versions as MutableList).clear() }
    }

    @Test
    fun traversalDoesNotInstantiateOrExecuteMigrations() {
        val prepared = MigrationVersionPreparationService().prepare(configuration.registrations.first())
        val plan = traversalService.plan(
            MigrationVersionSelection(MigrationVersionSelectionDirection.UP, listOf(prepared)),
        )

        assertEquals(0, MigrationProbe.constructorCalls)
        assertEquals(0, MigrationProbe.upCalls)
        assertEquals(0, MigrationProbe.downCalls)
        assertEquals(listOf("v1"), plan.versions.map { it.version.name })
    }

    @Test
    fun rejectsUnknownPersistedVersionRecord() {
        val base = snapshot(1)
        val invalid = MigrationVersionHistorySnapshot(
            tags = base.tags + MigrationHistoryTag(99, "removed_version", MigrationTagType.VERSION.code),
            versions = base.versions + MigrationHistoryVersion(999, 99),
            migrations = base.migrations,
        )

        assertInvalid(invalid, "not present in the configuration")
    }

    @Test
    fun rejectsDuplicateVersionRecordsForOneTag() {
        val base = snapshot(1)
        val invalid = MigrationVersionHistorySnapshot(
            base.tags,
            base.versions + MigrationHistoryVersion(101, VERSION_TAG_ID_1),
            base.migrations,
        )

        assertInvalid(invalid, "same Version Tag")
    }

    @Test
    fun rejectsDuplicateAppliedRows() {
        val base = snapshot(1)
        val invalid = MigrationVersionHistorySnapshot(
            base.tags,
            base.versions,
            base.migrations + MigrationHistoryEntry(2000, ITEM_TAG_IDS.getValue("m1"), VERSION_ID_1),
        )

        assertInvalid(invalid, "Duplicate applied Migration history")
    }

    @Test
    fun rejectsHistoryGapEvenWhenVersionIdsContainAllowedGaps() {
        val base = snapshot(3)
        val invalid = MigrationVersionHistorySnapshot(
            base.tags,
            listOf(
                base.versions.first(),
                base.versions.first { it.tagId == VERSION_TAG_ID_3 }.copy(id = 305),
            ),
            base.migrations.map { row ->
                if (row.versionId == VERSION_ID_3) row.copy(versionId = 305) else row
            }.filter { it.versionId == VERSION_ID_1 || it.versionId == 305 },
        )

        assertInvalid(invalid, "where 'v2' was expected")
    }

    @Test
    fun rejectsVersionIdHistoryOrderThatConflictsWithConfiguration() {
        val base = snapshot(2)
        val invalid = MigrationVersionHistorySnapshot(
            base.tags,
            listOf(
                MigrationHistoryVersion(100, VERSION_TAG_ID_2),
                MigrationHistoryVersion(200, VERSION_TAG_ID_1),
            ),
            base.migrations.map { row ->
                row.copy(versionId = if (row.versionId == VERSION_ID_1) 200 else 100)
            },
        )

        assertInvalid(invalid, "order conflicts with MigrationConfiguration")
    }

    @Test
    fun rejectsMissingAndUnexpectedMigrationHistory() {
        val base = snapshot(1)
        val missing = MigrationVersionHistorySnapshot(base.tags, base.versions, base.migrations.dropLast(1))
        assertInvalid(missing, "has 0 history rows")

        val extraTag = MigrationHistoryTag(888, "not_registered", MigrationTagType.ITEM.code)
        val unexpected = MigrationVersionHistorySnapshot(
            base.tags + extraTag,
            base.versions,
            base.migrations + MigrationHistoryEntry(1888, extraTag.id, VERSION_ID_1),
        )
        assertInvalid(unexpected, "not present in the configuration")
    }

    @Test
    fun allowsVersionTagsRetainedAfterTheirVersionRowsAreRolledBack() {
        val history = snapshot(appliedVersionCount = 0)

        assertTrue(history.versions.isEmpty())
        assertTrue(history.tags.any { it.type == MigrationTagType.VERSION.code })
        assertTrue(historyValidator.validateConfiguration(configuration, history).appliedVersions.isEmpty())
    }

    @Test
    fun allowsAnExistingItemTagWithoutHistoryForRunUpToReuse() {
        val base = snapshot(appliedVersionCount = 1)
        val orphan = MigrationHistoryTag(999, "orphan_item", MigrationTagType.ITEM.code)

        val state = historyValidator.validateConfiguration(
            configuration,
            MigrationVersionHistorySnapshot(base.tags + orphan, base.versions, base.migrations),
        )

        assertEquals(listOf("v1"), state.appliedVersions.map { it.version.name })
    }

    @Test
    fun rejectsMigrationsRecordedUnderWrongVersionAndUnknownTagReferences() {
        val base = snapshot(2)
        val wrongVersion = MigrationVersionHistorySnapshot(
            base.tags,
            base.versions,
            base.migrations.map { row ->
                if (row.tagId == ITEM_TAG_IDS.getValue("m1")) row.copy(versionId = VERSION_ID_2) else row
            },
        )
        assertInvalid(wrongVersion, "different persisted Version")

        val danglingTag = MigrationVersionHistorySnapshot(
            base.tags,
            base.versions,
            base.migrations.map { row ->
                if (row.id == base.migrations.first().id) row.copy(tagId = 9999) else row
            },
        )
        assertInvalid(danglingTag, "references missing Tag")
    }

    @Test
    fun scopeEntryReadsAllHistoryThroughTheCallerScopeWithoutStartingTransactionOrClosingConnection() {
        val jdbc = SnapshotJdbc(
            tags = listOf(
                mapOf("id" to VERSION_TAG_ID_1, "tagName" to "v1", "tagType" to MigrationTagType.VERSION.code),
                mapOf("id" to ITEM_TAG_IDS.getValue("m1"), "tagName" to "m1", "tagType" to MigrationTagType.ITEM.code),
                mapOf("id" to ITEM_TAG_IDS.getValue("m2"), "tagName" to "m2", "tagType" to MigrationTagType.ITEM.code),
            ),
            versions = listOf(mapOf("id" to VERSION_ID_1, "tagId" to VERSION_TAG_ID_1)),
            applied = listOf(
                mapOf("id" to 1001, "tagId" to ITEM_TAG_IDS.getValue("m1"), "versionId" to VERSION_ID_1),
                mapOf("id" to 1002, "tagId" to ITEM_TAG_IDS.getValue("m2"), "versionId" to VERSION_ID_1),
            ),
        )
        val scope = OperationExecutionScope(
            jdbc.connection,
            DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
        )

        val result = MigrationVersionHistoryReader().read(scope)

        val history = when (result) {
            is ExecuteResult.Success -> requireNotNull(result.result)
            is ExecuteResult.Failure -> error(result.exception.stackTraceToString())
        }
        val state = historyValidator.validateConfiguration(configuration, history)
        assertEquals(listOf("v1"), state.appliedVersions.map { it.version.name })
        assertEquals(3, jdbc.queryCount)
        assertEquals(0, jdbc.transactionControlCalls)
        assertEquals(0, jdbc.connectionCloseCount)
        assertEquals(3, jdbc.resultSetCloseCount)
    }

    @Test
    fun scopedHistoryReaderRejectsSqlNullForEveryNumericHistoryColumn() {
        val tags = listOf(
            mapOf("id" to VERSION_TAG_ID_1, "tagName" to "v1", "tagType" to MigrationTagType.VERSION.code),
            mapOf("id" to ITEM_TAG_IDS.getValue("m1"), "tagName" to "m1", "tagType" to MigrationTagType.ITEM.code),
            mapOf("id" to ITEM_TAG_IDS.getValue("m2"), "tagName" to "m2", "tagType" to MigrationTagType.ITEM.code),
        )
        val versions = listOf(mapOf("id" to VERSION_ID_1, "tagId" to VERSION_TAG_ID_1))
        val applied = listOf(
            mapOf("id" to 1001, "tagId" to ITEM_TAG_IDS.getValue("m1"), "versionId" to VERSION_ID_1),
            mapOf("id" to 1002, "tagId" to ITEM_TAG_IDS.getValue("m2"), "versionId" to VERSION_ID_1),
        )
        val cases = listOf(
            NullHistoryColumn("Tag.id", withNull(tags, "id"), versions, applied, "Tag query returned a null id"),
            NullHistoryColumn(
                "Tag.tagType",
                withNull(tags, "tagType"),
                versions,
                applied,
                "Migration Tag query returned a null type",
            ),
            NullHistoryColumn(
                "Version.id",
                tags,
                withNull(versions, "id"),
                applied,
                "Migration Version query returned a null id",
            ),
            NullHistoryColumn(
                "Version.tagId",
                tags,
                withNull(versions, "tagId"),
                applied,
                "Migration Version query returned a null tagId",
            ),
            NullHistoryColumn(
                "AppliedMigration.id",
                tags,
                versions,
                withNull(applied, "id"),
                "Applied Migration query returned a null id",
            ),
            NullHistoryColumn(
                "AppliedMigration.tagId",
                tags,
                versions,
                withNull(applied, "tagId"),
                "Applied Migration query returned a null tagId",
            ),
            NullHistoryColumn(
                "AppliedMigration.versionId",
                tags,
                versions,
                withNull(applied, "versionId"),
                "Applied Migration query returned a null versionId",
            ),
        )

        cases.forEach { case ->
            val jdbc = SnapshotJdbc(case.tags, case.versions, case.applied)
            val scope = OperationExecutionScope(
                jdbc.connection,
                DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
            )

            val result = MigrationVersionHistoryReader().read(scope)

            val failure = assertIs<ExecuteResult.Failure>(result, case.description)
            assertTrue(
                failure.exception.message.orEmpty().contains(case.expectedFailure),
                "${case.description}: ${failure.exception.message}",
            )
            assertEquals(0, jdbc.transactionControlCalls, case.description)
            assertEquals(0, jdbc.connectionCloseCount, case.description)
        }
    }

    private fun withNull(rows: List<Map<String, Any?>>, column: String): List<Map<String, Any?>> =
        rows.mapIndexed { index, row -> if (index == 0) row + (column to null) else row }

    private data class NullHistoryColumn(
        val description: String,
        val tags: List<Map<String, Any?>>,
        val versions: List<Map<String, Any?>>,
        val applied: List<Map<String, Any?>>,
        val expectedFailure: String,
    )

    private fun assertInvalid(snapshot: MigrationVersionHistorySnapshot, messagePart: String) {
        val failure = assertFailsWith<IllegalStateException> {
            historyValidator.validateConfiguration(configuration, snapshot)
        }
        assertTrue(failure.message.orEmpty().contains(messagePart, ignoreCase = true), failure.message)
    }

    private fun traversalVersion(name: String) =
        PreparedMigrationVersion(MigrationVersion(name, emptyList()), emptyList())

    private fun createConfiguration(): MigrationConfiguration = MigrationConfiguration(
        listOf(
            MigrationVersion(
                "v1",
                listOf(
                    MigrationGroup(
                        "foundation",
                        listOf(
                            SingleMigration(registered("m1", FirstMigration::class)),
                            MigrationGroup("details", listOf(SingleMigration(registered("m2", SecondMigration::class)))),
                        ),
                    ),
                ),
            ),
            MigrationVersion("v2", listOf(SingleMigration(registered("m3", ThirdMigration::class)))),
            MigrationVersion(
                "v3",
                listOf(
                    SingleMigration(registered("m4", FourthMigration::class)),
                    SingleMigration(registered("m5", FifthMigration::class)),
                ),
            ),
            MigrationVersion("v4", listOf(SingleMigration(registered("m6", SixthMigration::class)))),
        ),
    )

    private fun snapshot(appliedVersionCount: Int): MigrationVersionHistorySnapshot {
        val allVersions = listOf(
            "v1" to VERSION_TAG_ID_1,
            "v2" to VERSION_TAG_ID_2,
            "v3" to VERSION_TAG_ID_3,
            "v4" to VERSION_TAG_ID_4,
        )
        val allMigrations = listOf(
            listOf("m1", "m2"),
            listOf("m3"),
            listOf("m4", "m5"),
            listOf("m6"),
        )
        val tags = buildList {
            allVersions.forEach { (name, id) -> add(MigrationHistoryTag(id, name, MigrationTagType.VERSION.code)) }
            allMigrations.take(appliedVersionCount).flatten().forEach { name ->
                add(MigrationHistoryTag(ITEM_TAG_IDS.getValue(name), name, MigrationTagType.ITEM.code))
            }
        }
        val versions = allVersions.take(appliedVersionCount).mapIndexed { index, (_, tagId) ->
            MigrationHistoryVersion(VERSION_IDS[index], tagId)
        }
        var historyId = 1000
        val applied = allMigrations.take(appliedVersionCount).flatMapIndexed { index, names ->
            names.map { name ->
                MigrationHistoryEntry(historyId++, ITEM_TAG_IDS.getValue(name), VERSION_IDS[index])
            }
        }
        return MigrationVersionHistorySnapshot(tags, versions, applied)
    }

    private fun registered(id: String, migrationClass: KClass<out Migration>) =
        RegisteredMigration(MigrationIdentity.fromValue(id), migrationClass)

    private abstract class ProbeMigration(item: MigrationItemTag) : Migration(item) {
        init { MigrationProbe.constructorCalls++ }
        override fun up(): MigrationDefinition { MigrationProbe.upCalls++; error("Planning must not run up") }
        override fun down(): MigrationDefinition { MigrationProbe.downCalls++; error("Planning must not run down") }
    }

    private class FirstMigration : ProbeMigration(Items.M1)
    private class SecondMigration : ProbeMigration(Items.M2)
    private class ThirdMigration : ProbeMigration(Items.M3)
    private class FourthMigration : ProbeMigration(Items.M4)
    private class FifthMigration : ProbeMigration(Items.M5)
    private class SixthMigration : ProbeMigration(Items.M6)

    private object MigrationProbe {
        var constructorCalls = 0
        var upCalls = 0
        var downCalls = 0
    }

    private enum class Items(override val value: String) : MigrationItemTag {
        M1("m1"), M2("m2"), M3("m3"), M4("m4"), M5("m5"), M6("m6"),
    }

    private inner class SnapshotJdbc(
        tags: List<Map<String, Any?>>,
        versions: List<Map<String, Any?>>,
        applied: List<Map<String, Any?>>,
    ) {
        private val responses = ArrayDeque(listOf(tags, versions, applied))
        var queryCount = 0
            private set
        var transactionControlCalls = 0
            private set
        var connectionCloseCount = 0
            private set
        var resultSetCloseCount = 0
            private set

        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> preparedStatement()
                "commit", "rollback", "setAutoCommit" -> { transactionControlCalls++; null }
                "close" -> { connectionCloseCount++; null }
                "isClosed" -> false
                else -> primitiveDefault(method.returnType)
            }
        }

        private fun preparedStatement(): PreparedStatement = proxy(PreparedStatement::class.java) { method, _ ->
            when (method.name) {
                "executeQuery" -> {
                    queryCount++
                    resultSet(responses.removeFirst())
                }
                "close" -> null
                "isClosed" -> false
                else -> primitiveDefault(method.returnType)
            }
        }

        private fun resultSet(rows: List<Map<String, Any?>>): ResultSet {
            var index = -1
            var wasNull = false
            return proxy(ResultSet::class.java) { method, args ->
                when (method.name) {
                    "next" -> ++index < rows.size
                    "getInt", "getString" -> {
                        val value = rows[index][args[0].toString()]
                        wasNull = value == null
                        if (method.name == "getInt") (value as? Number)?.toInt() ?: 0 else value?.toString()
                    }
                    "wasNull" -> wasNull
                    "close" -> { resultSetCloseCount++; null }
                    "isClosed" -> false
                    else -> primitiveDefault(method.returnType)
                }
            }
        }
    }

    private fun <T> proxy(type: Class<T>, invoke: (java.lang.reflect.Method, Array<out Any?>) -> Any?): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                invoke(method, args ?: emptyArray())
            },
        )

    private fun primitiveDefault(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private companion object {
        const val VERSION_TAG_ID_1 = 1
        const val VERSION_TAG_ID_2 = 2
        const val VERSION_TAG_ID_3 = 3
        const val VERSION_TAG_ID_4 = 4
        const val VERSION_ID_1 = 100
        const val VERSION_ID_2 = 200
        const val VERSION_ID_3 = 300
        const val VERSION_ID_4 = 400
        val VERSION_IDS = listOf(VERSION_ID_1, VERSION_ID_2, VERSION_ID_3, VERSION_ID_4)
        val ITEM_TAG_IDS = mapOf("m1" to 11, "m2" to 12, "m3" to 13, "m4" to 14, "m5" to 15, "m6" to 16)
    }
}
