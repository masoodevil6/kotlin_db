package gog.my_project.data_base_manager.internal

import gog.my_project.data_base_migration.core.tags.MigrationTags

/** Immutable construction result shared by one DatabaseManager and all its registrations. */
internal class DatabaseManagerConfiguration(
    val registrations: List<DatabaseRegistration>,
    val migrationTags: MigrationTags = MigrationTags(),
)
