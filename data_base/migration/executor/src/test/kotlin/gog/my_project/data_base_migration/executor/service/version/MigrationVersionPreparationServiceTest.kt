package gog.my_project.data_base_migration.executor.service.version

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
import service.version.MigrationVersionPreparationService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationVersionPreparationServiceTest {
    private val service = MigrationVersionPreparationService()

    private enum class Versions(override val value: String) : MigrationVersionTag {
        CUSTOMERS("customers_v1"),
        EMPTY("empty_v1"),
        UNKNOWN("missing"),
        BLANK(" \t "),
    }

    @Test
    fun preparesOnlySelectedVersionAndPreservesNestedDeclarationOrder() {
        val first = registered("create_customers", FirstMigration::class)
        val second = registered("add_customer_email", SecondMigration::class)
        val third = registered("add_customer_index", ThirdMigration::class)
        val otherVersionMigration = registered("create_events", OtherMigration::class)
        val selectedVersion = MigrationVersion(
            "customers_v1",
            listOf(
                SingleMigration(first),
                MigrationGroup(
                    "customer_details",
                    listOf(
                        SingleMigration(second),
                        MigrationGroup("indexes", listOf(SingleMigration(third))),
                    ),
                ),
            ),
        )
        val otherVersion = MigrationVersion("events_v1", listOf(SingleMigration(otherVersionMigration)))
        val configuration = MigrationConfiguration(listOf(selectedVersion, otherVersion))

        val prepared = service.prepare(configuration, Versions.CUSTOMERS)

        assertSame(selectedVersion, prepared.version)
        assertEquals(listOf(first, second, third), prepared.migrations)
        assertTrue(prepared.migrations.none { it == otherVersionMigration })
    }

    @Test
    fun preservesEmptyVersion() {
        val version = MigrationVersion("empty_v1", emptyList())

        val prepared = service.prepare(MigrationConfiguration(listOf(version)), Versions.EMPTY)

        assertSame(version, prepared.version)
        assertTrue(prepared.migrations.isEmpty())
    }

    @Test
    fun rejectsInvalidAndUnknownVersionTags() {
        val configuration = MigrationConfiguration(listOf(MigrationVersion("v1", emptyList())))

        assertFailsWith<IllegalArgumentException> { service.prepare(configuration, Versions.BLANK) }
        val exception = assertFailsWith<IllegalArgumentException> {
            service.prepare(configuration, Versions.UNKNOWN)
        }
        assertTrue(exception.message.orEmpty().contains("missing"))
    }

    @Test
    fun preparationDoesNotMutateDefinitionsOrInstantiateMigrations() {
        MigrationLifecycle.constructors = 0
        val migration = registered("tracked_migration", ObservableMigration::class)
        val originalRegistrations = mutableListOf(SingleMigration(migration))
        val version = MigrationVersion("v1", originalRegistrations)
        val configuration = MigrationConfiguration(listOf(version))
        originalRegistrations.clear()

        val prepared = service.prepare(configuration, TestVersion("v1"))

        assertEquals(listOf(migration), prepared.migrations)
        assertEquals(1, version.registrations.size)
        assertEquals(0, MigrationLifecycle.constructors)
        assertFailsWith<UnsupportedOperationException> {
            (prepared.migrations as MutableList).clear()
        }
        assertEquals(listOf(migration), prepared.migrations)
    }

    private fun registered(
        id: String,
        migrationClass: kotlin.reflect.KClass<out Migration>,
    ) = RegisteredMigration(MigrationIdentity.fromValue(id), migrationClass)

    private enum class Items(override val value: String) : MigrationItemTag {
        TEST("test_migration"),
    }

    private data class TestVersion(override val value: String) : MigrationVersionTag

    private abstract class TestMigration : Migration(Items.TEST) {
        override fun up(): MigrationDefinition = error("Preparation must not execute Migration.up()")
        override fun down(): MigrationDefinition = error("Preparation must not execute Migration.down()")
    }

    private class FirstMigration : TestMigration()
    private class SecondMigration : TestMigration()
    private class ThirdMigration : TestMigration()
    private class OtherMigration : TestMigration()

    private object MigrationLifecycle {
        var constructors = 0
    }

    private class ObservableMigration : TestMigration() {
        init {
            MigrationLifecycle.constructors++
        }
    }
}
