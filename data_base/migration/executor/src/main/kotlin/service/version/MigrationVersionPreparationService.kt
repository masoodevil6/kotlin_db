package service.version

import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.MigrationGroup
import gog.my_project.data_base_migration.api.registration.group.MigrationNode
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import model.prepared.PreparedMigrationVersion
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag

/** Selects a registered Version and prepares its migration registrations without executing them. */
internal class MigrationVersionPreparationService {
    /**
     * Selects a Version by its existing name and flattens nested groups in declaration order.
     * MigrationConfiguration and its registration nodes already provide immutable ordered lists,
     * so that order is the established deterministic execution order.
     */
    fun prepare(
        configuration: MigrationConfiguration,
        versionTag: MigrationVersionTag,
    ): PreparedMigrationVersion {
        val versionName = versionTag.validatedValue

        val version = configuration.registrations.firstOrNull { it.name == versionName }
            ?: throw IllegalArgumentException("Migration version '$versionName' is not registered")

        return prepare(version)
    }

    /** Prepares an already-selected registration without changing its declaration order. */
    fun prepare(version: MigrationVersion): PreparedMigrationVersion {
        val migrations = buildList {
            version.registrations.forEach { registration -> collectMigrations(registration, this) }
        }

        return PreparedMigrationVersion(version, migrations)
    }

    private fun collectMigrations(
        node: MigrationNode,
        target: MutableList<RegisteredMigration>,
    ) {
        when (node) {
            is SingleMigration -> target += node.migration
            is MigrationGroup -> node.registrations.forEach { collectMigrations(it, target) }
        }
    }
}
