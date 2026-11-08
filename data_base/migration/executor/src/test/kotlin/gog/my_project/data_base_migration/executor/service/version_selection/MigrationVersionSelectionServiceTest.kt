package gog.my_project.data_base_migration.executor.service.version_selection

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
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelection
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelectionDirection
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationVersionSelectionServiceTest {
    private val configuration = createConfiguration()
    private val preparationService = MigrationVersionPreparationService()
    private val service = MigrationVersionSelectionService(configuration, preparationService)

    @Test
    fun upFromV3ToV5IncludesTargetInAscendingConfigurationOrder() {
        assertSelection("v3", Versions.V5, MigrationVersionSelectionDirection.UP, "v4", "v5")
    }

    @Test
    fun upFromV3ToV4IncludesOnlyTarget() {
        assertSelection("v3", Versions.V4, MigrationVersionSelectionDirection.UP, "v4")
    }

    @Test
    fun upWithoutTargetSelectsAllVersionsAfterCurrent() {
        assertSelection("v3", null, MigrationVersionSelectionDirection.UP, "v4", "v5")
    }

    @Test
    fun upWithoutCurrentStartsAtV1AndIncludesExplicitTarget() {
        assertSelection(null, Versions.V3, MigrationVersionSelectionDirection.UP, "v1", "v2", "v3")
    }

    @Test
    fun upWithoutCurrentOrTargetSelectsEveryConfiguredVersion() {
        assertSelection(null, null, MigrationVersionSelectionDirection.UP, "v1", "v2", "v3", "v4", "v5")
    }

    @Test
    fun upRejectsCurrentOrEarlierTarget() {
        assertFailure("v3", Versions.V3, MigrationVersionSelectionDirection.UP, "already been applied")
        assertFailure("v3", Versions.V2, MigrationVersionSelectionDirection.UP, "already been applied")
    }

    @Test
    fun downToV1IncludesCurrentAndExcludesTargetInDescendingOrder() {
        assertSelection("v3", Versions.V1, MigrationVersionSelectionDirection.DOWN, "v3", "v2")
    }

    @Test
    fun downToV2IncludesCurrentAndExcludesTarget() {
        assertSelection("v3", Versions.V2, MigrationVersionSelectionDirection.DOWN, "v3")
    }

    @Test
    fun downWithoutTargetSelectsOnlyCurrentVersion() {
        assertSelection("v3", null, MigrationVersionSelectionDirection.DOWN, "v3")
    }

    @Test
    fun downToCurrentReturnsEmptySelectionWithDownDirection() {
        val selection = select("v3", Versions.V3, MigrationVersionSelectionDirection.DOWN)

        assertEquals(MigrationVersionSelectionDirection.DOWN, selection.direction)
        assertTrue(selection.versions.isEmpty())
    }

    @Test
    fun downRejectsTargetLaterThanCurrent() {
        assertFailure("v3", Versions.V4, MigrationVersionSelectionDirection.DOWN, "later than the current")
    }

    @Test
    fun downWithoutCurrentAndTargetReturnsEmptyDownSelection() {
        val selection = select(null, null, MigrationVersionSelectionDirection.DOWN)

        assertEquals(MigrationVersionSelectionDirection.DOWN, selection.direction)
        assertTrue(selection.versions.isEmpty())
    }

    @Test
    fun downWithTargetAndNoCurrentFails() {
        assertFailure(
            currentName = null,
            target = Versions.V1,
            direction = MigrationVersionSelectionDirection.DOWN,
            message = "no Version is applied",
        )
    }

    @Test
    fun emptyConfigurationSupportsEmptyUnboundedSelectionAndRejectsExplicitTarget() {
        val emptyService = MigrationVersionSelectionService(MigrationConfiguration(emptyList()))
        val up = success(emptyService.select(null, null, MigrationVersionSelectionDirection.UP))
        val down = success(emptyService.select(null, null, MigrationVersionSelectionDirection.DOWN))

        assertEquals(MigrationVersionSelectionDirection.UP, up.direction)
        assertTrue(up.versions.isEmpty())
        assertEquals(MigrationVersionSelectionDirection.DOWN, down.direction)
        assertTrue(down.versions.isEmpty())
        assertIs<ExecuteResult.Failure>(
            emptyService.select(null, Versions.V1, MigrationVersionSelectionDirection.UP),
        )
    }

    @Test
    fun rejectsUnknownAndInvalidTargets() {
        assertFailure(
            currentName = null,
            target = Versions.UNKNOWN,
            direction = MigrationVersionSelectionDirection.UP,
            message = "not registered",
        )
        assertIs<ExecuteResult.Failure>(service.select(null, Versions.BLANK, MigrationVersionSelectionDirection.UP))
    }

    @Test
    fun rethrowsFatalErrorWithoutWrappingItAsFailure() {
        val fatal = AssertionError("fatal version tag failure")
        val brokenTag = object : MigrationVersionTag {
            override val value: String
                get() = throw fatal
        }

        val thrown = assertFailsWith<AssertionError> {
            service.select(null, brokenTag, MigrationVersionSelectionDirection.UP)
        }

        assertSame(fatal, thrown)
    }

    @Test
    fun rejectsCurrentVersionThatCannotBeMatchedToConfiguration() {
        val unknownCurrent = PreparedMigrationVersion(MigrationVersion("outside", emptyList()), emptyList())

        assertFailureWithCurrent(unknownCurrent, null, MigrationVersionSelectionDirection.UP, "Current Migration Version")
    }

    @Test
    fun preservesNestedMigrationGroupDeclarationOrder() {
        val selection = select(null, Versions.V2, MigrationVersionSelectionDirection.UP)
        val selectedV2 = selection.versions.last()

        assertEquals(listOf("m2a", "m2b", "m2c"), selectedV2.migrations.map { it.id.value })
    }

    @Test
    fun selectionListIsUnmodifiableAndContainsPreparedConfigurationVersions() {
        val current = current("v2")
        val selection = selectWithCurrent(current, Versions.V4, MigrationVersionSelectionDirection.UP)

        assertSame(configuration.registrations[2], selection.versions[0].version)
        assertFailsWith<UnsupportedOperationException> {
            (selection.versions as MutableList).clear()
        }
    }

    private fun assertSelection(
        currentName: String?,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
        vararg expectedNames: String,
    ) {
        val selection = selectWithCurrent(currentName?.let(::current), target, direction)

        assertEquals(direction, selection.direction)
        assertEquals(expectedNames.toList(), selection.versions.map { it.version.name })
    }

    private fun assertFailure(
        currentName: String?,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
        message: String,
    ) = assertFailureWithCurrent(currentName?.let(::current), target, direction, message)

    private fun assertFailureWithCurrent(
        current: PreparedMigrationVersion?,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
        message: String,
    ) {
        val failure = assertIs<ExecuteResult.Failure>(service.select(current, target, direction))
        assertTrue(failure.exception.message.orEmpty().contains(message, ignoreCase = true))
    }

    private fun select(
        currentName: String?,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
    ): MigrationVersionSelection = selectWithCurrent(currentName?.let(::current), target, direction)

    private fun selectWithCurrent(
        current: PreparedMigrationVersion?,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
    ): MigrationVersionSelection = success(service.select(current, target, direction))

    private fun success(result: ExecuteResult<MigrationVersionSelection>): MigrationVersionSelection =
        requireNotNull(assertIs<ExecuteResult.Success<MigrationVersionSelection>>(result).result)

    private fun current(name: String): PreparedMigrationVersion =
        preparationService.prepare(configuration.registrations.single { it.name == name })

    private fun createConfiguration(): MigrationConfiguration = MigrationConfiguration(
        listOf(
            MigrationVersion("v1", emptyList()),
            MigrationVersion(
                "v2",
                listOf(
                    SingleMigration(registered("m2a")),
                    MigrationGroup(
                        "nested",
                        listOf(
                            SingleMigration(registered("m2b")),
                            MigrationGroup("inner", listOf(SingleMigration(registered("m2c")))),
                        ),
                    ),
                ),
            ),
            MigrationVersion("v3", emptyList()),
            MigrationVersion("v4", emptyList()),
            MigrationVersion("v5", emptyList()),
        ),
    )

    private fun registered(id: String) =
        RegisteredMigration(MigrationIdentity.fromValue(id), SelectionTestMigration::class)

    private enum class Versions(override val value: String) : MigrationVersionTag {
        V1("v1"), V2("v2"), V3("v3"), V4("v4"), V5("v5"), UNKNOWN("missing"), BLANK("  "),
    }

    private enum class Items(override val value: String) : MigrationItemTag {
        TEST("selection_test_migration"),
    }

    private class SelectionTestMigration : Migration(Items.TEST) {
        override fun up(): MigrationDefinition = error("Selection must not execute Migration.up()")
        override fun down(): MigrationDefinition = error("Selection must not execute Migration.down()")
    }
}
