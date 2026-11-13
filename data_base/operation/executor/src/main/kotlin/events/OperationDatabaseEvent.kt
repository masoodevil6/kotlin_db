package gog.my_project.data_base_operation.executor.events

import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseEventFailure

/** Public operation boundary event types exposed by the Watch API. */
enum class OperationDatabaseEventType {
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

/** Safe representation of an Operation event for the public Watch API. */
data class OperationDatabaseEvent(
    val type: OperationDatabaseEventType,
    val sql: String? = null,
    val failure: DatabaseEventFailure? = null,
) : DatabaseEvent
