package gog.my_project.data_base_manager.connection.interfaces

import java.sql.Connection

/**
 * Optional Provider capability for evicting an unusable borrowed Connection from reuse.
 * Implementations must exclude the lease from healthy reuse even if shutdown reports a failure.
 * This contract does not itself guarantee that the database server has confirmed physical session
 * termination.
 */
interface IDatabaseConnectionInvalidator {
    fun discard(connection: Connection)
}
