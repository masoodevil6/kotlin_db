package gog.my_project.data_base_migration.api.model.migration_state

/** Selects which persisted Migration Version is inspected. */
enum class MigrationVersionSelection {
    /** The Version with the greatest persisted ID, regardless of status. */
    LATEST,

    /** The greatest persisted ID whose raw status is not the completed status. */
    LATEST_INCOMPLETE,
}
