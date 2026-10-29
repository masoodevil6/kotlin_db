package gog.my_project.data_base.manager.execute.connection

import gog.my_project.data_base.manager.connection.manager.DatabaseConnection
import java.sql.Connection

/** Acquires and owns one independent connection for the duration of [operation]. */
internal inline fun <T> withOwnedConnection(operation: (Connection) -> T): T =
    DatabaseConnection().build().use(operation)
