package gog.my_project.data_base_migration.executor.service.version

/** Internal Version-level result; a paused Retry is a successful boundary, not an execution failure. */
internal sealed interface MigrationVersionFlowOutcome {
    data object Completed : MigrationVersionFlowOutcome
    data object Paused : MigrationVersionFlowOutcome
}
