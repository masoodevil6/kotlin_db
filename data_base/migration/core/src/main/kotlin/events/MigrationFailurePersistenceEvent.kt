package gog.my_project.data_base_migration.core.events

import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseEventFailure

/** Whether Migration attempted a Status-only write or one atomic Status/Checkpoint CAS. */
enum class MigrationFailurePersistenceWriteMode {
    STATUS_ONLY,
    STATUS_CHECKPOINT_CAS,
}

/** Outcome of one requested Migration failure persistence write. */
enum class MigrationFailurePersistenceOutcome {
    APPLIED,
    NOT_APPLIED,
    INDETERMINATE,
    NOT_ATTEMPTED,
}

/** Safe result of persisting the failure state for one Migration Version. */
data class MigrationFailurePersistenceEvent(
    val operationId: String?,
    val versionId: Int,
    val writeMode: MigrationFailurePersistenceWriteMode,
    val statusOutcome: MigrationFailurePersistenceOutcome,
    val checkpointOutcome: MigrationFailurePersistenceOutcome,
    val failure: DatabaseEventFailure? = null,
) : DatabaseEvent
