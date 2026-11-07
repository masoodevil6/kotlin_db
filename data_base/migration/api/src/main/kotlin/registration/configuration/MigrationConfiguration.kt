package gog.my_project.data_base_migration.api.registration.configuration

import gog.my_project.data_base_migration.api.registration.immutableSnapshot
import gog.my_project.data_base_migration.api.registration.group.MigrationGroup
import gog.my_project.data_base_migration.api.registration.group.MigrationNode
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion

import gog.my_project.data_base_migration.core.migrate.MigrationIdentity

class MigrationConfiguration(registrations: List<MigrationVersion>) {
    val registrations: List<MigrationVersion> = immutableSnapshot(registrations)

    init {
        val seenVersionNames = mutableSetOf<String>()
        val seen = LinkedHashMap<MigrationIdentity, RegisteredMigration>()
        this.registrations.forEach { version ->
            require(seenVersionNames.add(version.name)) {
                "Duplicate Migration version name '${version.name}'"
            }
            version.registrations.forEach { registration -> validateMigrationNode(registration, seen) }
        }
    }
}

private fun validateMigrationNode(
    node: MigrationNode,
    seen: MutableMap<MigrationIdentity, RegisteredMigration>,
) {
    when (node) {
        is SingleMigration -> validateMigration(node.migration, seen)
        is MigrationGroup -> node.registrations.forEach { validateMigrationNode(it, seen) }
    }
}

private fun validateMigration(
    migration: RegisteredMigration,
    seen: MutableMap<MigrationIdentity, RegisteredMigration>,
) {
    val previous = seen.putIfAbsent(migration.id, migration)
    require(previous == null) {
        "Duplicate MigrationId '${migration.id.value}' registered by " +
            "${previous?.migrationClass?.qualifiedName} and ${migration.migrationClass.qualifiedName}"
    }
}
