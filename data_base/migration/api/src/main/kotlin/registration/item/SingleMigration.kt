package gog.my_project.data_base_migration.api.registration.group

import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration

data class SingleMigration(
    val migration: RegisteredMigration,
) : MigrationNode
