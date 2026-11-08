package model.migration_status

/** Status of a configured or persisted Migration Version. */
enum class MigrationVersionStatus {
    APPLIED,
    PENDING,
    UNKNOWN,
    INVALID,
}
