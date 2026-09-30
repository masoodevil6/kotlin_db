package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationGroup
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.api.interfaces.MigrationIdentity
import gog.my_project.data_base.migration.api.interfaces.RegisteredMigration
import gog.my_project.data_base.migration.api.interfaces.SingleMigration
import gog.my_project.data_base.migration.builder.migrationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private object migrationTags {
    const val create_users = "create_users"
    const val add_users_email = "add_users_email"
    const val add_users_index = "add_users_index"
    const val duplicate = "duplicate_id"
    const val blank = "   "
    const val invalid = "create__users"
    const val uppercase = "CreateUsers"
}

class MigrationConfigurationBuilderTest {
    @Test
    fun readsAnnotationConstantAndPreservesTopLevelAndGroupOrdering() {
        val configuration = migrationConfig {
            migration<CreateUsers>()
            migrationGroup("users") {
                migration<AddUsersEmail>()
                migration<AddUsersIndex>()
            }
            migration<ArchiveUsers>()
        }

        assertEquals(3, configuration.registrations.size)
        val first = assertIs<SingleMigration>(configuration.registrations[0]).migration
        assertEquals("create_users", first.id.value)
        assertEquals(CreateUsers::class, first.migrationClass)

        val group = assertIs<MigrationGroup>(configuration.registrations[1])
        assertEquals("users", group.name)
        assertEquals(
            listOf("add_users_email", "add_users_index"),
            group.migrations.map { it.id.value },
        )
        assertEquals(ArchiveUsers::class, assertIs<SingleMigration>(configuration.registrations[2]).migration.migrationClass)
    }

    @Test
    fun failsWhenAnnotationIsMissing() {
        val exception = assertFailsWith<IllegalArgumentException> {
            migrationConfig { migration<MissingId>() }
        }

        assertEquals(true, exception.message.orEmpty().contains("must declare @MigrationId"))
        assertEquals(true, exception.message.orEmpty().contains(MissingId::class.java.name))
    }

    @Test
    fun failsForBlankOrInvalidAnnotationValues() {
        val blank = assertFailsWith<IllegalArgumentException> {
            migrationConfig { migration<BlankId>() }
        }
        assertEquals(true, blank.message.orEmpty().contains("must not be blank"))

        val invalid = assertFailsWith<IllegalArgumentException> {
            migrationConfig { migration<InvalidId>() }
        }
        assertEquals(true, invalid.message.orEmpty().contains("must match"))

        assertFailsWith<IllegalArgumentException> {
            migrationConfig { migration<UppercaseId>() }
        }
    }

    @Test
    fun rejectsDuplicateIdAcrossRegistrationTreeAndNamesBothDeclarations() {
        val exception = assertFailsWith<IllegalArgumentException> {
            migrationConfig {
                migration<FirstDuplicate>()
                migrationGroup("users") {
                    migration<SecondDuplicate>()
                }
            }
        }

        assertEquals(true, exception.message.orEmpty().contains("duplicate_id"))
        assertEquals(true, exception.message.orEmpty().contains(FirstDuplicate::class.qualifiedName.orEmpty()))
        assertEquals(true, exception.message.orEmpty().contains(SecondDuplicate::class.qualifiedName.orEmpty()))
    }

    @Test
    fun preservesEmptyConfigurationEmptyGroupsRepeatedEntriesAndGroupNames() {
        assertEquals(emptyList(), migrationConfig { }.registrations)
        val configuration = migrationConfig {
            migrationGroup("users") { }
            migrationGroup("users") { }
            migration<DistinctOne>()
            migration<DistinctTwo>()
        }
        assertEquals(4, configuration.registrations.size)
        assertEquals(emptyList(), assertIs<MigrationGroup>(configuration.registrations[0]).migrations)
        assertEquals(emptyList(), assertIs<MigrationGroup>(configuration.registrations[1]).migrations)
        assertEquals("distinct_one", assertIs<SingleMigration>(configuration.registrations[2]).migration.id.value)
        assertEquals("distinct_two", assertIs<SingleMigration>(configuration.registrations[3]).migration.id.value)
    }

    @Test
    fun rejectsBlankGroupNameAndPreservesItsOriginalSpelling() {
        assertFailsWith<IllegalArgumentException> { migrationConfig { migrationGroup(" \t ") { } } }
        val group = assertIs<MigrationGroup>(
            migrationConfig { migrationGroup(" users ") { } }.registrations.single(),
        )
        assertEquals(" users ", group.name)
    }

    @Test
    fun registrationDoesNotInstantiateMigrationOrCallUpOrDown() {
        MigrationLifecycle.reset()

        val configuration: MigrationConfiguration = migrationConfig {
            migration<ObservableMigration>()
        }

        assertEquals(0, MigrationLifecycle.constructors)
        assertEquals(0, MigrationLifecycle.upCalls)
        assertEquals(0, MigrationLifecycle.downCalls)
        assertEquals(ObservableMigration::class, assertIs<SingleMigration>(configuration.registrations.single()).migration.migrationClass)
    }

    @Test
    fun registeredIdentityValueIsTheExactAnnotationString() {
        val configuration = migrationConfig { migration<EmailMigration>() }
        val registered = assertIs<SingleMigration>(configuration.registrations.single()).migration

        assertEquals("add_users_email", registered.id.value)
        assertEquals(MigrationIdentity.fromAnnotationValue("add_users_email"), registered.id)
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

private abstract class TestMigration : Migration {
    override fun up() = MigrationLifecycle.runUp()
    override fun down() = MigrationLifecycle.runDown()
}

@MigrationId(migrationTags.create_users)
private class CreateUsers : TestMigration()

@MigrationId(migrationTags.add_users_email)
private class AddUsersEmail : TestMigration()

@MigrationId(migrationTags.add_users_index)
private class AddUsersIndex : TestMigration()

@MigrationId("archive_users")
private class ArchiveUsers : TestMigration()

private class MissingId : TestMigration()

@MigrationId(migrationTags.blank)
private class BlankId : TestMigration()

@MigrationId(migrationTags.invalid)
private class InvalidId : TestMigration()

@MigrationId(migrationTags.uppercase)
private class UppercaseId : TestMigration()

@MigrationId(migrationTags.duplicate)
private class FirstDuplicate : TestMigration()

@MigrationId(migrationTags.duplicate)
private class SecondDuplicate : TestMigration()

@MigrationId("distinct_one")
private class DistinctOne : TestMigration()

@MigrationId("distinct_two")
private class DistinctTwo : TestMigration()

@MigrationId(migrationTags.add_users_email)
private class EmailMigration : TestMigration()

@MigrationId("observable_migration")
private class ObservableMigration : Migration {
    init {
        MigrationLifecycle.constructors++
    }

    override fun up(): MigrationDefinition {
        MigrationLifecycle.upCalls++
        return migration { }
    }

    override fun down(): MigrationDefinition {
        MigrationLifecycle.downCalls++
        return migration { }
    }
}

private fun MigrationLifecycle.runUp(): Nothing {
    upCalls++
    error("Migration.up() must not run during registration")
}

private fun MigrationLifecycle.runDown(): Nothing {
    downCalls++
    error("Migration.down() must not run during registration")
}
