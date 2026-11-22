package gog.my_project.data_base_migration.api.model.migration_state

/** Read-only classification of the operation represented by a persisted Version status. */
enum class MigrationRecoveryOperation {
    UP_EXECUTION,
    UP_RECORDING_EXECUTION,
    DOWN_EXECUTION,
    DOWN_RECORDING_EXECUTION,
    START_EXECUTION,
    MIGRATE_DONE,
    UNKNOWN,
    UP_MANUAL_RECOVERY_REQUIRED,
    DOWN_MANUAL_RECOVERY_REQUIRED,
}
