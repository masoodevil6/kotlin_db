package model.migration_status

/** Status of a configured or persisted Migration item. */
enum class MigrationItemStatus {
    APPLIED,
    PENDING,
    UNKNOWN,
    INVALID,
}
