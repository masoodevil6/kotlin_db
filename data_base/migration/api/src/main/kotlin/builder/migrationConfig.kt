package gog.my_project.data_base_migration.api.builder

import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration

fun migrationConfig(block: MigrationConfigurationBuilder.() -> Unit): MigrationConfiguration =
    MigrationConfigurationBuilder().apply(block).build()
