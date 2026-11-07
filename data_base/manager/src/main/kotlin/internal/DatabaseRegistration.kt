package gog.my_project.data_base_manager.internal

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.facade.DatabaseKey

/** Runtime registration state owned by DatabaseManager, not part of its public facade. */
internal class DatabaseRegistration(
    val key: DatabaseKey,
    val config: DatabaseConfig,
) {
    @Volatile
    var context: DatabaseContext? = null
}
