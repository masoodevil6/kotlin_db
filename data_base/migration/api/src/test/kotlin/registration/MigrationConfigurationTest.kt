package gog.my_project.data_base_migration.api.registration

import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.MigrationGroup
import gog.my_project.data_base_migration.api.registration.group.MigrationNode
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MigrationConfigurationTest {
    @Test
    fun snapshotsVersionAndNestedGroupCollectionsInTheirDeclaredOrder() {
        val first = registered("create_users", FirstMigration::class)
        val second = registered("add_email", SecondMigration::class)
        val third = registered("add_index", ThirdMigration::class)
        val groupMembers = mutableListOf<MigrationNode>(SingleMigration(second), SingleMigration(third))
        val group = MigrationGroup("users", groupMembers)
        val versionRegistrations = mutableListOf<MigrationNode>(SingleMigration(first), group)
        val version = MigrationVersion("v1", versionRegistrations)
        val source = mutableListOf(version)

        val configuration = MigrationConfiguration(source)
        source.clear()
        versionRegistrations.clear()
        groupMembers.clear()

        assertEquals(1, configuration.registrations.size)
        assertSame(version, configuration.registrations.single())
        assertEquals("v1", version.name)
        assertEquals(2, version.registrations.size)
        assertSame(first, assertIs<SingleMigration>(version.registrations[0]).migration)
        val preservedGroup = assertIs<MigrationGroup>(version.registrations[1])
        assertEquals("users", preservedGroup.name)
        assertEquals(listOf(second, third), preservedGroup.registrations.map { assertIs<SingleMigration>(it).migration })
    }

    @Test
    fun groupsAcceptNestedGroupsAndPreserveOrderWithoutFlattening() {
        val first = SingleMigration(registered("create_users", FirstMigration::class))
        val nested = MigrationGroup(
            "nested",
            listOf(SingleMigration(registered("add_email", SecondMigration::class))),
        )
        val group = MigrationGroup("users", listOf(first, nested))

        assertSame(first, group.registrations[0])
        assertSame(nested, group.registrations[1])
        assertEquals("nested", assertIs<MigrationGroup>(group.registrations[1]).name)
    }

    @Test
    fun registrationCollectionsAreReadOnlySnapshots() {
        val groupMembers = mutableListOf<MigrationNode>(
            SingleMigration(registered("add_email", SecondMigration::class)),
        )
        val group = MigrationGroup("users", groupMembers)
        val versionMembers = mutableListOf<MigrationNode>(group)
        val version = MigrationVersion("v1", versionMembers)
        val source = mutableListOf(version)
        val configuration = MigrationConfiguration(source)

        groupMembers.clear()
        versionMembers.clear()
        source.clear()

        assertFailsWith<UnsupportedOperationException> {
            (configuration.registrations as MutableList<MigrationVersion>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (version.registrations as MutableList<MigrationNode>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (group.registrations as MutableList<MigrationNode>).clear()
        }
        assertEquals(1, group.registrations.size)
    }

    @Test
    fun rejectsDuplicateIdentityAcrossVersionsAndNestedGroups() {
        val first = registered("create_users", FirstMigration::class)
        val duplicate = registered("create_users", SecondMigration::class)

        val exception = assertFailsWith<IllegalArgumentException> {
            MigrationConfiguration(
                listOf(
                    MigrationVersion("v1", listOf(SingleMigration(first))),
                    MigrationVersion(
                        "v2",
                        listOf(MigrationGroup("users", listOf(SingleMigration(duplicate)))),
                    ),
                ),
            )
        }

        assertEquals(true, exception.message.orEmpty().contains("create_users"))
        assertEquals(true, exception.message.orEmpty().contains(FirstMigration::class.qualifiedName.orEmpty()))
        assertEquals(true, exception.message.orEmpty().contains(SecondMigration::class.qualifiedName.orEmpty()))
    }

    @Test
    fun rejectsDuplicateIdentityBetweenDirectAndGroupedNodesWithinAVersion() {
        assertFailsWith<IllegalArgumentException> {
            MigrationConfiguration(
                listOf(
                    MigrationVersion(
                        "v1",
                        listOf(
                            SingleMigration(registered("create_users", FirstMigration::class)),
                            MigrationGroup(
                                "users",
                                listOf(SingleMigration(registered("create_users", SecondMigration::class))),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsDuplicateVersionNames() {
        assertFailsWith<IllegalArgumentException> {
            MigrationConfiguration(
                listOf(
                    MigrationVersion("v1", emptyList()),
                    MigrationVersion("v1", emptyList()),
                ),
            )
        }
    }

    @Test
    fun acceptsOnlyVersionsAtConfigurationLevelAndAllowsEmptyConfiguration() {
        val configuration = MigrationConfiguration(
            listOf(MigrationVersion("v1", listOf(MigrationGroup("users", emptyList())))),
        )

        assertEquals(1, configuration.registrations.size)
        assertEquals(emptyList(), MigrationConfiguration(emptyList()).registrations)
    }

    @Test
    fun rejectsBlankContainerNamesAndPreservesNonBlankSpelling() {
        assertFailsWith<IllegalArgumentException> { MigrationVersion(" \t\n", emptyList()) }
        assertFailsWith<IllegalArgumentException> { MigrationGroup("  ", emptyList()) }
        assertEquals(" v1 ", MigrationVersion(" v1 ", emptyList()).name)
        assertEquals(" users ", MigrationGroup(" users ", emptyList()).name)
    }

    private fun registered(
        id: String,
        migrationClass: kotlin.reflect.KClass<out Migration>,
    ) = RegisteredMigration(MigrationIdentity.fromValue(id), migrationClass)

    private enum class TestItems(override val value: String) : MigrationItemTag {
        FIRST("create_users"),
        SECOND("add_email"),
        THIRD("add_index"),
    }

    private abstract class TestMigration(item: MigrationItemTag) : Migration(item) {
        override fun up() = error("Registration metadata must not execute migrations")
        override fun down() = error("Registration metadata must not execute migrations")
    }

    private class FirstMigration : TestMigration(TestItems.FIRST)
    private class SecondMigration : TestMigration(TestItems.SECOND)
    private class ThirdMigration : TestMigration(TestItems.THIRD)
}
