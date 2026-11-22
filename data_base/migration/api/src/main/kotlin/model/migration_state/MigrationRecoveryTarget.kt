package gog.my_project.data_base_migration.api.model.migration_state

import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration

/** Persisted Version state plus only an unambiguously resolved configured Migration, if available. */
data class MigrationRecoveryTarget(
    val version: MigrationVersionState,
    val migration: RegisteredMigration?,
    val operation: MigrationRecoveryOperation,
) {
    /**
     * Checkpoint generation captured with this state snapshot, when the persisted checkpoint was
     * valid. This is a freshness selector only; Retry must re-read and validate database state.
     *
     * This property is intentionally outside the primary constructor to preserve the existing
     * three-argument JVM constructor, data-class components, and `copy()` signature. Kotlin's
     * generated `copy()` does not copy this body property, so a copied target has no revision and
     * must be rejected by Retry validation.
     */
    val checkpointRevision: Long?
        get() = checkpointRevisionSnapshot

    private var checkpointRevisionSnapshot: Long? = null

    /** Additive constructor for a Target resolved from a persisted Checkpoint snapshot. */
    constructor(
        version: MigrationVersionState,
        migration: RegisteredMigration?,
        operation: MigrationRecoveryOperation,
        checkpointRevision: Long?,
    ) : this(version, migration, operation) {
        require(checkpointRevision == null || checkpointRevision >= 0) {
            "Checkpoint revision must not be negative"
        }
        checkpointRevisionSnapshot = checkpointRevision
    }
}
