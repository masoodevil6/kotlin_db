package gog.my_project.data_base_manager.execute.connection

import gog.my_project.data_base_manager.connection.manager.DatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import java.sql.Connection

/** Acquires and owns one independent connection for the duration of [operation]. */
internal inline fun <T> withOwnedConnection(operation: (Connection) -> T): T =
    DatabaseConnection().build().use(operation)

/** Acquires and owns one independent connection from the selected database context. */
internal inline fun <T> withOwnedConnection(
    databaseContext: DatabaseContext,
    operation: (Connection) -> T,
): T = databaseContext.openConnection().use(operation)
