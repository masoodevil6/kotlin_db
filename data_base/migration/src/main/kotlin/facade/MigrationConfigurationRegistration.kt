package gog.my_project.data_base_migration.facade

import gog.my_project.data_base_manager.facade.DatabaseRegistrationAttribute
import gog.my_project.data_base_manager.facade.DatabaseRegistrationBuilder
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration

/** Stable Migration-owned key used to attach configuration to one Manager registration. */
internal object MigrationConfigurationRegistration {
    val key: DatabaseRegistrationAttribute<MigrationConfiguration> =
        DatabaseRegistrationAttribute.create()
}

/**
 * Assign the Migration configuration for this database registration.
 *
 * This property is intended for the registration DSL setter; reading it during registration is
 * unsupported because the generic attribute builder intentionally exposes only typed writes.
 */
var DatabaseRegistrationBuilder.migrationConfiguration: MigrationConfiguration
    get() = throw UnsupportedOperationException(
        "Migration configuration is write-only in the Database registration DSL",
    )
    set(value) {
        attribute(MigrationConfigurationRegistration.key, value)
    }
