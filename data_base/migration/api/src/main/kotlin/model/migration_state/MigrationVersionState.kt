package gog.my_project.data_base_migration.api.model.migration_state

/**
 * Read-only view of the latest persisted Migration Version.
 *
 * [status] is the raw database value. Values not listed in this object's constants are retained
 * unchanged so callers can detect statuses introduced by a newer or incompatible writer.
 */
data class MigrationVersionState(
    val versionId: Int,
    val tagId: Int,
    val status: Int,
    /** The persisted diagnostic JSON, returned without parsing or transformation. */
    val message: String?,
) {
    companion object {
        const val STATUS_MIGRATE_STARTED = 100
        const val STATUS_MIGRATE_FAILED_UP = 101
        const val STATUS_MIGRATE_FAILED_RECORD = 102
        const val STATUS_MIGRATE_COMPLETED = 103
        const val STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED = 110

        const val STATUS_ROLLBACK_STARTED = 200
        const val STATUS_ROLLBACK_FAILED_DOWN = 201
        const val STATUS_ROLLBACK_FAILED_RECORD = 202
        const val STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED = 210
    }
}
