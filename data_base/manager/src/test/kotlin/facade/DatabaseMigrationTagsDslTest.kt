package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.MigrationGroup
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationGroupTag
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseMigrationTagsDslTest {
    private object MainDatabase : DatabaseKey
    private object AnalyticsDatabase : DatabaseKey

    @Test
    fun definitionAssignmentKeepsTypedEnumEntriesInEachCoreCategory() {
        val configuration = DatabaseCatalogBuilder().apply {
            migrationTags = DataBaseMigrationTags
        }.snapshot()

        assertSame(DataBaseMigrationTags.Versions.Init, configuration.migrationTags.versionTags.single())
        assertSame(DataBaseMigrationTags.Groups.CUSTOMERS, configuration.migrationTags.groupTags.single())
        assertSame(DataBaseMigrationTags.Items.CREATE_CUSTOMERS, configuration.migrationTags.itemTags.single())
    }

    @Test
    fun publicDatabaseManagerConstructorAcceptsDefinitionAssignment() {
        val manager: DatabaseManagerApi = DatabaseManager {
            migrationTags = DataBaseMigrationTags
        }

        try {
            assertTrue(manager.registeredKeys().isEmpty())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun oneManagerTagCatalogIsSharedAcrossAllDatabaseRegistrations() {
        val configuration = DatabaseCatalogBuilder().apply {
            migrationTags = DataBaseMigrationTags
            database(MainDatabase) { config = databaseConfig("main") }
            database(AnalyticsDatabase) { config = databaseConfig("analytics") }
        }.snapshot()

        assertEquals(listOf(MainDatabase, AnalyticsDatabase), configuration.registrations.map { it.key })
        assertEquals(listOf("Init"), configuration.migrationTags.versionTags.map { it.value })
        assertEquals(listOf("customers"), configuration.migrationTags.groupTags.map { it.value })
        assertEquals(listOf("create_customers"), configuration.migrationTags.itemTags.map { it.value })
    }

    @Test
    fun suppliedTagListsAreSnapshottedWhenTheCatalogIsBuilt() {
        val definition = MutableTagDefinition()
        val builder = DatabaseCatalogBuilder().apply { migrationTags = definition }
        val configuration = builder.snapshot()

        definition.versionValues += VersionValue("v2")
        definition.groupValues += GroupValue("extra")
        definition.itemValues += ItemValue("update_customers")

        assertEquals(listOf("v1"), configuration.migrationTags.versionTags.map { it.value })
        assertEquals(listOf("customers"), configuration.migrationTags.groupTags.map { it.value })
        assertEquals(listOf("create_customers"), configuration.migrationTags.itemTags.map { it.value })
    }

    @Test
    fun duplicateTagsAreRejectedWithinEachCategory() {
        assertFailsWith<IllegalArgumentException> {
            DatabaseCatalogBuilder().apply {
                migrationTags = MutableTagDefinition(
                    versionValues = mutableListOf(VersionValue("v1"), VersionValue("v1")),
                )
            }.snapshot()
        }
        assertFailsWith<IllegalArgumentException> {
            DatabaseCatalogBuilder().apply {
                migrationTags = MutableTagDefinition(
                    groupValues = mutableListOf(GroupValue("users"), GroupValue("users")),
                )
            }.snapshot()
        }
        assertFailsWith<IllegalArgumentException> {
            DatabaseCatalogBuilder().apply {
                migrationTags = MutableTagDefinition(
                    itemValues = mutableListOf(ItemValue("create_users"), ItemValue("create_users")),
                )
            }.snapshot()
        }
    }

    @Test
    fun invalidNamesAreValidatedByCoreTagContracts() {
        assertFailsWith<IllegalArgumentException> {
            DatabaseCatalogBuilder().apply {
                migrationTags = MutableTagDefinition(versionValues = mutableListOf(VersionValue("  ")))
            }.snapshot()
        }
        assertFailsWith<IllegalArgumentException> {
            DatabaseCatalogBuilder().apply {
                migrationTags = MutableTagDefinition(groupValues = mutableListOf(GroupValue("")))
            }.snapshot()
        }
        assertFailsWith<IllegalArgumentException> {
            DatabaseCatalogBuilder().apply {
                migrationTags = MutableTagDefinition(itemValues = mutableListOf(ItemValue("Not_A_Migration")))
            }.snapshot()
        }
    }

    @Test
    fun sameNameAcrossCategoriesIsAllowed() {
        val configuration = DatabaseCatalogBuilder().apply {
            migrationTags = MutableTagDefinition(
                versionValues = mutableListOf(VersionValue("shared")),
                groupValues = mutableListOf(GroupValue("shared")),
                itemValues = mutableListOf(ItemValue("shared")),
            )
        }.snapshot()

        assertEquals("shared", configuration.migrationTags.versionTags.single().value)
        assertEquals("shared", configuration.migrationTags.groupTags.single().value)
        assertEquals("shared", configuration.migrationTags.itemTags.single().value)
    }

    @Test
    fun managerAndMigrationConfigurationUseTheSameTypedTags() {
        val managerTags = DatabaseCatalogBuilder().apply {
            migrationTags = DataBaseMigrationTags
            database(MainDatabase) { config = databaseConfig("main") }
        }.snapshot().migrationTags

        val migrations: MigrationConfiguration = migrationConfig {
            migrationVersion(DataBaseMigrationTags.Versions.Init) {
                migrationGroup(DataBaseMigrationTags.Groups.CUSTOMERS) {
                    migration<SharedTaggedMigration>()
                }
            }
        }

        val version = assertIs<MigrationVersion>(migrations.registrations.single())
        val group = assertIs<MigrationGroup>(version.registrations.single())
        val registered = assertIs<SingleMigration>(group.registrations.single()).migration

        assertSame(managerTags.versionTags.single(), version.tag)
        assertSame(managerTags.groupTags.single(), group.tag)
        assertEquals(MigrationIdentity.fromValue(managerTags.itemTags.single().value), registered.id)
    }

    @Test
    fun migrationTagsCanOnlyBeAssignedOncePerManager() {
        assertFailsWith<IllegalStateException> {
            DatabaseCatalogBuilder().apply {
                migrationTags = DataBaseMigrationTags
                migrationTags = DataBaseMigrationTags
            }
        }
    }

    @Test
    fun omittedMigrationTagsUseAnEmptySetAndDatabaseRegistrationStillWorks() {
        val configuration = DatabaseCatalogBuilder().apply {
            database(MainDatabase) { config = databaseConfig("main") }
        }.snapshot()

        assertTrue(configuration.migrationTags.versionTags.isEmpty())
        assertTrue(configuration.migrationTags.groupTags.isEmpty())
        assertTrue(configuration.migrationTags.itemTags.isEmpty())
        assertEquals(listOf(MainDatabase), configuration.registrations.map { it.key })
    }

    private fun databaseConfig(name: String) = DatabaseConfig(
        connection = DatabaseConnectionConfig(
            dbDomain = "jdbc:mysql://localhost",
            dbPort = 3306,
            dbName = name,
            dbUserName = "test",
            dbPassword = "test",
        ),
    )
}

private object DataBaseMigrationTags : MigrationTagDefinition {
    enum class Versions(override val value: String) : MigrationVersionTag {
        Init("Init"),
    }

    enum class Groups(override val value: String) : MigrationGroupTag {
        CUSTOMERS("customers"),
    }

    enum class Items(override val value: String) : MigrationItemTag {
        CREATE_CUSTOMERS("create_customers"),
    }

    override val versions get() = Versions.entries
    override val groups get() = Groups.entries
    override val items get() = Items.entries
}

private data class VersionValue(override val value: String) : MigrationVersionTag
private data class GroupValue(override val value: String) : MigrationGroupTag
private data class ItemValue(override val value: String) : MigrationItemTag

private class MutableTagDefinition(
    val versionValues: MutableList<MigrationVersionTag> = mutableListOf(VersionValue("v1")),
    val groupValues: MutableList<MigrationGroupTag> = mutableListOf(GroupValue("customers")),
    val itemValues: MutableList<MigrationItemTag> = mutableListOf(ItemValue("create_customers")),
) : MigrationTagDefinition {
    override val versions: List<MigrationVersionTag> get() = versionValues
    override val groups: List<MigrationGroupTag> get() = groupValues
    override val items: List<MigrationItemTag> get() = itemValues
}

private class SharedTaggedMigration : Migration(DataBaseMigrationTags.Items.CREATE_CUSTOMERS) {
    override fun up(): MigrationDefinition = error("Migration registration must not execute up()")
    override fun down(): MigrationDefinition = error("Migration registration must not execute down()")
}
