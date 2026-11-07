package gog.my_project.data_base_migration.api.registration.item

import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import kotlin.reflect.KClass

data class RegisteredMigration(
    val id: MigrationIdentity,
    val migrationClass: KClass<out Migration>,
)
