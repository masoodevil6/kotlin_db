package gog.my_project.data_base_manager.internal

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseRegistrationAttribute
import java.util.Collections
import java.util.LinkedHashMap

/** Runtime registration state owned by DatabaseManager, not part of its public facade. */
internal class DatabaseRegistration(
    val key: DatabaseKey,
    val config: DatabaseConfig,
    val lockIdentity: DatabaseIdentity?,
    attributes: Map<DatabaseRegistrationAttribute<*>, Any> = emptyMap(),
) {
    /** Defensive, read-only snapshot owned by this Registration. */
    val attributes: Map<DatabaseRegistrationAttribute<*>, Any> =
        Collections.unmodifiableMap(LinkedHashMap(attributes))

    @Volatile
    var context: DatabaseContext? = null
}
