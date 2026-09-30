package gog.my_project.data_base.migration.api.interfaces

import java.util.ArrayList
import java.util.Collections
import kotlin.reflect.KClass

data class RegisteredMigration(
    val id: MigrationIdentity,
    val migrationClass: KClass<out Migration>,
)

sealed interface MigrationRegistration

data class SingleMigration(
    val migration: RegisteredMigration,
) : MigrationRegistration

class MigrationGroup(
    name: String,
    migrations: List<RegisteredMigration>,
) : MigrationRegistration {
    val name: String = name.also {
        require(it.isNotBlank()) { "Migration group name must not be blank" }
    }

    val migrations: List<RegisteredMigration> = immutableSnapshot(migrations)
}

class MigrationConfiguration(registrations: List<MigrationRegistration>) {
    val registrations: List<MigrationRegistration> = immutableSnapshot(registrations)

    init {
        val seen = LinkedHashMap<MigrationIdentity, RegisteredMigration>()
        this.registrations.forEach { registration ->
            val migrations = when (registration) {
                is SingleMigration -> listOf(registration.migration)
                is MigrationGroup -> registration.migrations
            }
            migrations.forEach { migration ->
                val previous = seen.putIfAbsent(migration.id, migration)
                require(previous == null) {
                    "Duplicate MigrationId '${migration.id.value}' registered by " +
                        "${previous?.migrationClass?.qualifiedName} and ${migration.migrationClass.qualifiedName}"
                }
            }
        }
    }
}

private fun <T> immutableSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
