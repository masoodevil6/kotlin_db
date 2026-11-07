package gog.my_project.data_base_migration.api.builder

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

private enum class Versions(override val value: String) : MigrationVersionTag {
    V1("v1"),
    WITH_SPACES(" v1 "),
    BLANK(" \t "),
    TOO_LONG("v".repeat(256)),
}

private enum class Groups(override val value: String) : MigrationGroupTag {
    USERS("users"),
    NESTED("nested"),
    WITH_SPACES(" users "),
    BLANK(" \t "),
}

private enum class Items(override val value: String) : MigrationItemTag {
    CREATE_USERS("create_users"),
    ADD_USERS_EMAIL("add_users_email"),
    ADD_USERS_INDEX("add_users_index"),
    ARCHIVE_USERS("archive_users"),
    DUPLICATE_ONE("duplicate_id"),
    DUPLICATE_TWO("duplicate_id"),
    DISTINCT_ONE("distinct_one"),
    DISTINCT_TWO("distinct_two"),
    BLANK("   "),
    INVALID("create__users"),
    UPPERCASE("CreateUsers"),
    OBSERVABLE("observable_migration"),
}

class MigrationConfigurationBuilderTest {
    @Test
    fun buildsVersionWithOrderedMigrationsAndGroups() {
        val configuration = migrationConfig {
            migrationVersion(Versions.V1) {
                migration<CreateUsers>()
                migrationGroup(Groups.USERS) {
                    migration<AddUsersEmail>()
                    migration<AddUsersIndex>()
                }
                migration<ArchiveUsers>()
            }
        }

        assertEquals(1, configuration.registrations.size)
        val version = assertIs<MigrationVersion>(configuration.registrations.single())
        assertEquals("v1", version.name)
        assertEquals(Versions.V1, version.tag)
        assertEquals(3, version.registrations.size)
        val first = assertIs<SingleMigration>(version.registrations[0]).migration
        assertEquals("create_users", first.id.value)
        assertEquals(CreateUsers::class, first.migrationClass)

        val group = assertIs<MigrationGroup>(version.registrations[1])
        assertEquals("users", group.name)
        assertEquals(Groups.USERS, group.tag)
        assertEquals(
            listOf("add_users_email", "add_users_index"),
            group.registrations.map { assertIs<SingleMigration>(it).migration.id.value },
        )
        assertEquals(
            ArchiveUsers::class,
            assertIs<SingleMigration>(version.registrations[2]).migration.migrationClass,
        )
    }

    @Test
    fun identityComesFromMigrationClassAndLifecycleMethodsAreNotCalled() {
        MigrationLifecycle.reset()

        val configuration: MigrationConfiguration = migrationConfig {
            migrationVersion(Versions.V1) { migration<ObservableMigration>() }
        }

        assertEquals(1, MigrationLifecycle.constructors)
        assertEquals(0, MigrationLifecycle.upCalls)
        assertEquals(0, MigrationLifecycle.downCalls)
        val registered = assertIs<SingleMigration>(
            assertIs<MigrationVersion>(configuration.registrations.single()).registrations.single(),
        ).migration
        assertEquals("observable_migration", registered.id.value)
        assertEquals(ObservableMigration::class, registered.migrationClass)
    }

    @Test
    fun rejectsInvalidVersionGroupAndMigrationIdentityValues() {
        assertFailsWith<IllegalArgumentException> {
            migrationConfig { migrationVersion(Versions.BLANK) { } }
        }
        assertFailsWith<IllegalArgumentException> {
            migrationConfig { migrationVersion(Versions.TOO_LONG) { } }
        }
        assertFailsWith<IllegalArgumentException> {
            migrationConfig { migrationVersion(Versions.V1) { migrationGroup(Groups.BLANK) { } } }
        }
        assertFailsWith<IllegalArgumentException> {
            migrationConfig { migrationVersion(Versions.V1) { migration<BlankId>() } }
        }
        assertFailsWith<IllegalArgumentException> {
            migrationConfig { migrationVersion(Versions.V1) { migration<InvalidId>() } }
        }
        assertFailsWith<IllegalArgumentException> {
            migrationConfig { migrationVersion(Versions.V1) { migration<UppercaseId>() } }
        }
    }

    @Test
    fun rejectsDuplicateMigrationIdentityAcrossDirectAndNestedRegistrations() {
        val exception = assertFailsWith<IllegalArgumentException> {
            migrationConfig {
                migrationVersion(Versions.V1) {
                    migration<FirstDuplicate>()
                    migrationGroup(Groups.USERS) {
                        migration<SecondDuplicate>()
                    }
                }
            }
        }

        assertEquals(true, exception.message.orEmpty().contains("duplicate_id"))
        assertEquals(true, exception.message.orEmpty().contains(FirstDuplicate::class.qualifiedName.orEmpty()))
        assertEquals(true, exception.message.orEmpty().contains(SecondDuplicate::class.qualifiedName.orEmpty()))
    }

    @Test
    fun preservesEmptyConfigurationAndRecursivelyNestedGroups() {
        assertEquals(emptyList(), migrationConfig { }.registrations)
        val configuration = migrationConfig {
            migrationVersion(Versions.V1) {
                migrationGroup(Groups.USERS) {
                    migrationGroup(Groups.NESTED) { migration<DistinctOne>() }
                }
                migration<DistinctTwo>()
            }
        }
        val version = assertIs<MigrationVersion>(configuration.registrations.single())
        val outerGroup = assertIs<MigrationGroup>(version.registrations[0])
        val nestedGroup = assertIs<MigrationGroup>(outerGroup.registrations.single())

        assertEquals("distinct_one", assertIs<SingleMigration>(nestedGroup.registrations.single()).migration.id.value)
        assertEquals("distinct_two", assertIs<SingleMigration>(version.registrations[1]).migration.id.value)
    }

    @Test
    fun preservesNonBlankVersionAndGroupSpelling() {
        val configuration = migrationConfig {
            migrationVersion(Versions.WITH_SPACES) { migrationGroup(Groups.WITH_SPACES) { } }
        }
        val version = assertIs<MigrationVersion>(configuration.registrations.single())

        assertEquals(" v1 ", version.name)
        assertEquals(" users ", assertIs<MigrationGroup>(version.registrations.single()).name)
    }

    @Test
    fun identityValueUsesExistingMigrationIdentityValidation() {
        val configuration = migrationConfig {
            migrationVersion(Versions.V1) { migration<EmailMigration>() }
        }
        val registered = assertIs<SingleMigration>(
            assertIs<MigrationVersion>(configuration.registrations.single()).registrations.single(),
        ).migration

        assertEquals("add_users_email", registered.id.value)
        assertEquals(MigrationIdentity.fromValue("add_users_email"), registered.id)
    }
}

private object MigrationLifecycle {
    var constructors = 0
    var upCalls = 0
    var downCalls = 0

    fun reset() {
        constructors = 0
        upCalls = 0
        downCalls = 0
    }
}

private abstract class TestMigration(item: MigrationItemTag) : Migration(item) {
    override fun up(): MigrationDefinition = error("Migration.up() must not run during registration")
    override fun down(): MigrationDefinition = error("Migration.down() must not run during registration")
}

private class CreateUsers : TestMigration(Items.CREATE_USERS)
private class AddUsersEmail : TestMigration(Items.ADD_USERS_EMAIL)
private class AddUsersIndex : TestMigration(Items.ADD_USERS_INDEX)
private class ArchiveUsers : TestMigration(Items.ARCHIVE_USERS)
private class BlankId : TestMigration(Items.BLANK)
private class InvalidId : TestMigration(Items.INVALID)
private class UppercaseId : TestMigration(Items.UPPERCASE)
private class FirstDuplicate : TestMigration(Items.DUPLICATE_ONE)
private class SecondDuplicate : TestMigration(Items.DUPLICATE_TWO)
private class DistinctOne : TestMigration(Items.DISTINCT_ONE)
private class DistinctTwo : TestMigration(Items.DISTINCT_TWO)
private class EmailMigration : TestMigration(Items.ADD_USERS_EMAIL)

private class ObservableMigration : Migration(Items.OBSERVABLE) {
    init {
        MigrationLifecycle.constructors++
    }

    override fun up(): MigrationDefinition = error("Migration.up() must not run during registration")
    override fun down(): MigrationDefinition = error("Migration.down() must not run during registration")
}
