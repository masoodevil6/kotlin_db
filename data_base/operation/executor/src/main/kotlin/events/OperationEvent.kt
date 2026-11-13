package gog.my_project.data_base_operation.executor.events

import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseEventFailure
import gog.my_project.data_base.core.events.DatabaseEventListener

/** Events observed at the Operation-owned JDBC execution and lifecycle boundaries. */
enum class OperationEventType {
    SQL_STARTED,
    SQL_SUCCEEDED,
    SQL_FAILED,

    CONNECTION_OPENED,
    CONNECTION_OPEN_FAILED,
    CONNECTION_CLOSED,
    CONNECTION_CLOSE_FAILED,

    TRANSACTION_STARTED,
    TRANSACTION_START_FAILED,
    TRANSACTION_COMMITTED,
    TRANSACTION_COMMIT_FAILED,
    TRANSACTION_ROLLED_BACK,
    TRANSACTION_ROLLBACK_FAILED,
}

/**
 * Immutable observation of one Operation boundary.
 *
 * [sql] is the parameterized statement text passed to the execution backend (with JDBC `?`
 * placeholders where applicable), never the Query renderer's display SQL. Bound values are
 * deliberately omitted to avoid leaking credentials or other sensitive data through observers.
 * SQL success/failure covers the full scoped call, including synchronous row-consumption callbacks
 * and JDBC resource cleanup.
 */
data class OperationEvent(
    val type: OperationEventType,
    val sql: String? = null,
    val error: Throwable? = null,
)

/** A synchronous, read-only observer. Listener failures, including [Error]s, are isolated. */
fun interface OperationEventListener {
    fun onEvent(event: OperationEvent)

    companion object {
        /**
         * Adapts the public Watch listener to the existing Operation listener contract. The
         * adapter also carries domain events emitted by integrations running in the same Scope.
         */
        @JvmStatic
        fun fromDatabaseEventListener(listener: DatabaseEventListener): OperationEventListener =
            object : OperationEventListener, DatabaseEventPublisher {
                override fun onEvent(event: OperationEvent) {
                    listener.onEvent(event.toDatabaseEvent())
                }

                override fun publishDatabaseEvent(event: DatabaseEvent) {
                    try {
                        listener.onEvent(event)
                    } catch (_: Throwable) {
                        // Public observers are isolated from Operation and Migration semantics.
                    }
                }
            }
    }
}

/** Internal bridge retained by the Operation-owned Scope for domain events. */
internal interface DatabaseEventPublisher {
    fun publishDatabaseEvent(event: DatabaseEvent)
}

internal fun OperationEvent.toDatabaseEvent(): OperationDatabaseEvent = OperationDatabaseEvent(
    type = when (type) {
        OperationEventType.SQL_STARTED -> OperationDatabaseEventType.SQL_STARTED
        OperationEventType.SQL_SUCCEEDED -> OperationDatabaseEventType.SQL_SUCCEEDED
        OperationEventType.SQL_FAILED -> OperationDatabaseEventType.SQL_FAILED
        OperationEventType.CONNECTION_OPENED -> OperationDatabaseEventType.CONNECTION_OPENED
        OperationEventType.CONNECTION_OPEN_FAILED -> OperationDatabaseEventType.CONNECTION_OPEN_FAILED
        OperationEventType.CONNECTION_CLOSED -> OperationDatabaseEventType.CONNECTION_CLOSED
        OperationEventType.CONNECTION_CLOSE_FAILED -> OperationDatabaseEventType.CONNECTION_CLOSE_FAILED
        OperationEventType.TRANSACTION_STARTED -> OperationDatabaseEventType.TRANSACTION_STARTED
        OperationEventType.TRANSACTION_START_FAILED -> OperationDatabaseEventType.TRANSACTION_START_FAILED
        OperationEventType.TRANSACTION_COMMITTED -> OperationDatabaseEventType.TRANSACTION_COMMITTED
        OperationEventType.TRANSACTION_COMMIT_FAILED -> OperationDatabaseEventType.TRANSACTION_COMMIT_FAILED
        OperationEventType.TRANSACTION_ROLLED_BACK -> OperationDatabaseEventType.TRANSACTION_ROLLED_BACK
        OperationEventType.TRANSACTION_ROLLBACK_FAILED -> OperationDatabaseEventType.TRANSACTION_ROLLBACK_FAILED
    },
    sql = sql,
    failure = error?.let(DatabaseEventFailure::from),
)

/** Event observers are best-effort and must never become part of Operation failure handling. */
internal fun OperationEventListener?.publish(event: OperationEvent) {
    if (this == null) return
    try {
        onEvent(event)
    } catch (_: Throwable) {
        // Observation must not change SQL results, failure precedence, or resource lifecycle.
    }
}
