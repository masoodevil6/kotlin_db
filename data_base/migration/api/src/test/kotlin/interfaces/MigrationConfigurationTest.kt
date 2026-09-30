package gog.my_project.data_base.migration.api.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MigrationConfigurationTest {
    @Test
    fun snapshotsRegistrationAndGroupCollectionsInTheirDeclaredOrder() {
        val first = registered("create_users", FirstMigration::class)
        val second = registered("add_email", SecondMigration::class)
        val third = registered("add_index", ThirdMigration::class)
        val groupMembers = mutableListOf(second, third)
        val source = mutableListOf<MigrationRegistration>(
            SingleMigration(first),
            MigrationGroup("users", groupMembers),
        )

        val configuration = MigrationConfiguration(source)
        source.clear()
        groupMembers.clear()

        assertEquals(2, configuration.registrations.size)
        assertSame(first, assertIs<SingleMigration>(configuration.registrations[0]).migration)
        val group = assertIs<MigrationGroup>(configuration.registrations[1])
        assertEquals("users", group.name)
        assertEquals(listOf(second, third), group.migrations)
    }

    @Test
    fun registrationCollectionsAreReadOnly() {
        val group = MigrationGroup("users", listOf(registered("add_email", SecondMigration::class)))
        val configuration = MigrationConfiguration(listOf(SingleMigration(registered("create_users", FirstMigration::class)), group))

        assertFailsWith<UnsupportedOperationException> {
            (configuration.registrations as MutableList<MigrationRegistration>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (group.migrations as MutableList<RegisteredMigration>).clear()
        }
    }

    @Test
    fun rejectsDuplicateIdentityAcrossTopLevelAndGroupRegistrations() {
        val duplicate = registered("create_users", SecondMigration::class)

        val exception = assertFailsWith<IllegalArgumentException> {
            MigrationConfiguration(
                listOf(
                    SingleMigration(registered("create_users", FirstMigration::class)),
                    MigrationGroup("users", listOf(duplicate)),
                ),
            )
        }

        assertEquals(true, exception.message.orEmpty().contains("create_users"))
        assertEquals(true, exception.message.orEmpty().contains(FirstMigration::class.qualifiedName.orEmpty()))
        assertEquals(true, exception.message.orEmpty().contains(SecondMigration::class.qualifiedName.orEmpty()))
    }

    @Test
    fun permitsEmptyConfigurationAndRepeatedGroupNames() {
        val configuration = MigrationConfiguration(
            listOf(MigrationGroup("users", emptyList()), MigrationGroup("users", emptyList())),
        )

        assertEquals(2, configuration.registrations.size)
        assertEquals(emptyList(), MigrationConfiguration(emptyList()).registrations)
    }

    @Test
    fun rejectsBlankGroupNameAndPreservesNonBlankSpelling() {
        assertFailsWith<IllegalArgumentException> { MigrationGroup("  ", emptyList()) }
        assertEquals(" users ", MigrationGroup(" users ", emptyList()).name)
    }

    private fun registered(
        id: String,
        migrationClass: kotlin.reflect.KClass<out Migration>,
    ) = RegisteredMigration(MigrationIdentity.fromAnnotationValue(id), migrationClass)

    private abstract class TestMigration : Migration {
        override fun up() = error("Registration metadata must not execute migrations")
        override fun down() = error("Registration metadata must not execute migrations")
    }

    private class FirstMigration : TestMigration()
    private class SecondMigration : TestMigration()
    private class ThirdMigration : TestMigration()
}
