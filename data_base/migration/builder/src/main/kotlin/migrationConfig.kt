package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration

fun migrationConfig(block: MigrationConfigurationBuilder.() -> Unit): MigrationConfiguration =
    MigrationConfigurationBuilder().apply(block).build()
