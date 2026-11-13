package gog.my_project.data_base_migration.core.events

import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseEventFailure

/** Public Migration command categories represented by domain events. */
enum class MigrationCommand {
    MIGRATE,
    ROLLBACK,
}

/** Public Migration lifecycle event types. */
enum class MigrationEventType {
    MIGRATION_STARTED,
    MIGRATION_VERSION_STARTED,
    MIGRATION_VERSION_APPLIED,
    MIGRATION_VERSION_ROLLED_BACK,
    MIGRATION_COMPLETED,
    MIGRATION_FAILED,
}

/** Safe Migration lifecycle event emitted through the Operation-owned event path. */
data class MigrationEvent(
    val type: MigrationEventType,
    val command: MigrationCommand,
    val versionName: String? = null,
    val targetVersionName: String? = null,
    val failure: DatabaseEventFailure? = null,
) : DatabaseEvent
