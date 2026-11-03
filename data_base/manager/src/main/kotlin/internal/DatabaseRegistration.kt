package gog.my_project.data_base_manager.internal

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base_manager.facade.DatabaseKey

/** Runtime registration state owned by DatabaseManager, not part of its public facade. */
internal class DatabaseRegistration(
    val key: DatabaseKey,
    val config: DatabaseConfig,
    val migrations: MigrationConfiguration?,
) {
    @Volatile
    var context: DatabaseContext? = null
}
