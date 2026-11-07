package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_migration.core.tags.MigrationTags
import gog.my_project.data_base_manager.internal.DatabaseManagerConfiguration
import gog.my_project.data_base_manager.internal.DatabaseRegistration

/**
 * Construction-time DSL for registering typed databases owned by one [DatabaseManager].
 *
 * Each key may be registered once under its value-equality contract. Every registration must
 * provide a [DatabaseConfig]. The resulting catalog is snapshotted by
 * the manager constructor, so later changes to a builder do not mutate a running manager.
 */
class DatabaseCatalogBuilder {
    private val registrations = LinkedHashMap<DatabaseKey, DatabaseRegistration>()
    private var migrationTagsConfigured = false

    /**
     * Optional shared tag definition for this Manager. Values are converted and snapshotted when
     * the catalog is built.
     */
    var migrationTags: MigrationTagDefinition? = null
        set(value) {
            check(!migrationTagsConfigured) {
                "Migration tags may be configured only once per DatabaseManager"
            }
            migrationTagsConfigured = true
            field = value
        }

    /** Adds one database registration, rejecting a key already equal to a registered key. */
    fun database(
        key: DatabaseKey,
        block: DatabaseRegistrationBuilder.() -> Unit,
    ) {
        require(!registrations.containsKey(key)) {
            "Database key '$key' (type '${key::class.qualifiedName}') is already registered"
        }
        val registration = DatabaseRegistrationBuilder().apply(block).build(key)
        registrations[key] = registration
    }

    internal fun snapshot(): DatabaseManagerConfiguration = DatabaseManagerConfiguration(
        registrations = registrations.values.toList(),
        migrationTags = migrationTags?.let { definition ->
            MigrationTags(
                versionTags = definition.versions,
                groupTags = definition.groups,
                itemTags = definition.items,
            )
        } ?: MigrationTags(),
    )
}

/**
 * Configuration for one database registration. A connection context is not opened while this DSL
 * block is evaluated; runtime resources are initialized lazily when the corresponding handle first
 * performs an operation. [config] is required.
 */
class DatabaseRegistrationBuilder {
    /** Required non-secret and secret connection/platform configuration for this registration. */
    var config: DatabaseConfig? = null

    internal fun build(key: DatabaseKey): DatabaseRegistration {
        val databaseConfig = requireNotNull(config) {
            "Database '${key::class.qualifiedName}' must declare a DatabaseConfig"
        }
        return DatabaseRegistration(key, databaseConfig)
    }
}
