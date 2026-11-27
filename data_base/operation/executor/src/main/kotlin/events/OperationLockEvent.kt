package gog.my_project.data_base_operation.executor.events

import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseEventFailure

/** Semantic result observed at an Operation-owned advisory lock boundary. */
enum class OperationLockEventType {
    LOCK_ACQUISITION_STARTED,
    LOCK_ACQUIRED,
    LOCK_TIMED_OUT,
    LOCK_ACQUISITION_FAILED,
    LOCK_ACQUISITION_UNKNOWN,
    LOCK_RELEASE_STARTED,
    LOCK_RELEASED,
    LOCK_NOT_OWNED,
    LOCK_RELEASE_FAILED,
    LOCK_RELEASE_UNKNOWN,
}

/** Safe, correlated observation of one logical Operation lock attempt. */
data class OperationLockEvent(
    val type: OperationLockEventType,
    val operationId: String?,
    val lockAttemptId: String,
    val namespace: String,
    val name: String,
    val failure: DatabaseEventFailure? = null,
    val cleanupAfterUnknownAcquisition: Boolean = false,
) : DatabaseEvent
