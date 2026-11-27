package gog.my_project.data_base_operation.executor.transaction

import java.sql.Connection
import java.util.concurrent.Executor

/** Synchronous JDBC abort used when a Provider-specific discard capability is unavailable. */
internal fun abortOperationConnection(connection: Connection) {
    connection.abort(DIRECT_EXECUTOR)
}

private val DIRECT_EXECUTOR = Executor { command -> command.run() }
