package gog.my_project.data_base.migration.executor.internal.system_migration

/** One successfully applied migration as recorded by the internal state store. */
internal data class AppliedMigration(
    val id: Int,
    val migration: String,
    val batch: Int,
)

/** Snapshot read from the migration history. An empty history has no current batch. */
internal data class SystemMigrationState(
    val history: List<AppliedMigration>,
    val currentBatch: Int?,
)
