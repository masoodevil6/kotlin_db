package gog.my_project.data_base_manager.facade

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base_manager.internal.DatabaseRegistration

/** DSL for registering the databases owned by one [DatabaseManager]. */
class DatabaseCatalogBuilder {
    private val registrations = LinkedHashMap<DatabaseKey, DatabaseRegistration>()

    fun database(
        key: DatabaseKey,
        block: DatabaseRegistrationBuilder.() -> Unit,
    ) {
        require(!registrations.containsKey(key)) {
            "Database key '${key::class.qualifiedName}' is already registered"
        }
        val registration = DatabaseRegistrationBuilder().apply(block).build(key)
        registrations[key] = registration
    }

    internal fun snapshot(): List<DatabaseRegistration> = registrations.values.toList()
}

/** Configuration for one database; runtime resources are created lazily on first use. */
class DatabaseRegistrationBuilder {
    var config: DatabaseConfig? = null
    var migrations: MigrationConfiguration? = null

    internal fun build(key: DatabaseKey): DatabaseRegistration {
        val databaseConfig = requireNotNull(config) {
            "Database '${key::class.qualifiedName}' must declare a DatabaseConfig"
        }
        return DatabaseRegistration(key, databaseConfig, migrations)
    }
}
