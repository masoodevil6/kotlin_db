package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryOperation
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import model.version_history.MigrationHistoryVersion
import service.version.MigrationVersionPreparationService
import model.prepared.PreparedMigrationVersion
import model.retry.MigrationRetryCheckpoint

/** Reads persisted Migration Version state under the same lock used by Migration writes. */
internal class MigrationVersionStateService(
    private val configuration: MigrationConfiguration,
    private val reader: MigrationVersionHistoryReader = MigrationVersionHistoryReader(),
    private val preparationService: MigrationVersionPreparationService = MigrationVersionPreparationService(),
) {
    private val checkpointStatusValidator = MigrationCheckpointStatusValidator()
    private val retryTargetValidator = MigrationRetryTargetValidator()
    /** Compatibility path for the earlier Boolean selector. */
    fun state(
        scope: OperationExecutionScope,
        lastIncompleteVersion: Boolean = false,
    ): ExecuteResult<MigrationVersionState?> = withMigrationLock(scope) { lockedScope ->
        when (val result = reader.readLatestVersion(lockedScope, lastIncompleteVersion)) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> ExecuteResult.Success(result.result?.toPublicState())
        }
    }

    fun state(
        scope: OperationExecutionScope,
        selection: MigrationVersionSelection,
    ): ExecuteResult<MigrationRecoveryTarget?> = withMigrationLock(scope) { lockedScope ->
        val lastIncomplete = selection == MigrationVersionSelection.LATEST_INCOMPLETE
        when (val result = reader.readLatestVersion(lockedScope, lastIncomplete)) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> result.result?.let { resolveTarget(lockedScope, it) }
                ?: ExecuteResult.Success(null)
        }
    }

    fun state(
        scope: OperationExecutionScope,
        versionId: Int,
    ): ExecuteResult<MigrationRecoveryTarget?> = withMigrationLock(scope) { lockedScope ->
        when (val result = reader.readVersionById(lockedScope, versionId)) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> result.result?.let { resolveTarget(lockedScope, it) }
                ?: ExecuteResult.Success(null)
        }
    }

    /**
     * Re-reads and validates a Retry selector while the caller already owns the Migration lock.
     * This method deliberately does not acquire a lock and never calls the public state API.
     */
    internal fun validateTargetWhileMigrationLockHeld(
        scope: OperationExecutionScope,
        supplied: MigrationRecoveryTarget,
    ): ExecuteResult<MigrationRecoveryTarget> = when (
        val result = validateTargetSnapshotWhileMigrationLockHeld(scope, supplied)
    ) {
        is ExecuteResult.Failure -> result
        is ExecuteResult.Success -> ExecuteResult.Success(result.result?.target)
    }

    /** Returns the fresh locked Target together with the validated execution inputs. */
    internal fun validateTargetSnapshotWhileMigrationLockHeld(
        scope: OperationExecutionScope,
        supplied: MigrationRecoveryTarget,
    ): ExecuteResult<ValidatedMigrationRecoveryTarget> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        if (supplied.checkpointRevision == null) {
            return scope.failed(IllegalArgumentException("Retry Target has no Checkpoint revision"))
        }

        val selected = when (val result = reader.readVersionById(scope, supplied.version.versionId)) {
            is ExecuteResult.Failure -> return result
            is ExecuteResult.Success -> result.result
                ?: return scope.failed(IllegalArgumentException("Retry Target Version no longer exists"))
        }
        val resolved = when (val result = resolveTargetSnapshot(scope, selected)) {
            is ExecuteResult.Failure -> return result
            is ExecuteResult.Success -> result.result
                ?: return scope.failed(IllegalStateException("Retry Target resolution returned no value"))
        }

        return try {
            val freshTarget = retryTargetValidator.validate(
                    supplied = supplied,
                    current = resolved.target,
                    row = selected,
                    assessment = resolved.assessment,
                    configuredVersionResolved = resolved.configuredVersionResolved,
                    registeredMigrations = resolved.registeredMigrations,
                )
            ExecuteResult.Success(
                ValidatedMigrationRecoveryTarget(
                    target = freshTarget,
                    status = selected.status,
                    checkpoint = requireNotNull(resolved.assessment.checkpoint) {
                        "Validated Retry Target has no Checkpoint"
                    },
                    preparedVersion = requireNotNull(resolved.preparedVersion) {
                        "Validated Retry Target has no configured Version"
                    },
                ),
            )
        } catch (failure: IllegalArgumentException) {
            scope.failed(failure)
        }
    }

    private fun resolveTarget(
        scope: OperationExecutionScope,
        selected: MigrationHistoryVersion,
    ): ExecuteResult<MigrationRecoveryTarget> = when (val result = resolveTargetSnapshot(scope, selected)) {
        is ExecuteResult.Failure -> result
        is ExecuteResult.Success -> ExecuteResult.Success(result.result?.target)
    }

    private fun resolveTargetSnapshot(
        scope: OperationExecutionScope,
        selected: MigrationHistoryVersion,
    ): ExecuteResult<ResolvedMigrationRecoveryTarget> {
        val state = selected.toPublicState()
        val checkpointAssessment = checkpointStatusValidator.assess(selected.status, selected.retryCheckpoint)
        val tagsResult = reader.readTags(scope)
        if (tagsResult is ExecuteResult.Failure) return tagsResult
        val tags = requireNotNull((tagsResult as ExecuteResult.Success).result)

        // Resolve only through the persisted Version -> VERSION Tag -> configured Version link.
        // A missing, duplicated, or inconsistent relationship yields no guessed Migration.
        val matchingTags = tags.filter { it.id == selected.tagId }
        val versionTag = matchingTags.singleOrNull()
            ?.takeIf { tag -> tag.type == MigrationTagType.VERSION.code }
        val configuredVersion = versionTag?.let { tag ->
            configuration.registrations.singleOrNull { it.name == tag.name }
        }
        val preparedVersion = configuredVersion?.let(preparationService::prepare)
        val registeredMigrations = preparedVersion?.migrations.orEmpty()

        val diagnosticIdentity = selected.message?.let(::parseDiagnosticMigrationId)
        val migrationId = when {
            checkpointAssessment.checkpoint != null -> checkpointAssessment.checkpoint.cursorMigrationId
            // A persisted checkpoint is authoritative even when it cannot be decoded. Do not
            // replace an absent/invalid cursor with a Diagnostic or single-registration guess.
            selected.retryCheckpoint != null -> null
            diagnosticIdentity != null -> diagnosticIdentity.value
            else -> registeredMigrations.singleOrNull()?.id?.value
        }
        val migration = migrationId?.let { id ->
            registeredMigrations.singleOrNull { it.id.value == id }
        }

        return ExecuteResult.Success(
            ResolvedMigrationRecoveryTarget(
                target = MigrationRecoveryTarget(
                    version = state,
                    migration = migration,
                    operation = checkpointAssessment.toRecoveryOperation(selected.status),
                    checkpointRevision = checkpointAssessment.checkpoint?.revision,
                ),
                assessment = checkpointAssessment,
                configuredVersionResolved = configuredVersion != null,
                registeredMigrations = registeredMigrations,
                preparedVersion = preparedVersion,
            ),
        )
    }

    /** Returns a parsed identity only for the existing diagnostic JSON schema. */
    private fun parseDiagnosticMigrationId(message: String): DiagnosticMigrationId? {
        return try {
            val json = Json.parseToJsonElement(message) as? JsonObject ?: return null
            val schemaVersion = (json["schemaVersion"] as? JsonPrimitive)?.intOrNull
            if (schemaVersion != DIAGNOSTIC_SCHEMA_VERSION) return null
            if ("migrationId" !in json) return DiagnosticMigrationId(value = null)

            val element = json["migrationId"]
            when {
                element == JsonNull -> DiagnosticMigrationId(value = null)
                element is JsonPrimitive && element.isString ->
                    DiagnosticMigrationId(value = element.contentOrNull)
                else -> DiagnosticMigrationId(value = null)
            }
        } catch (_: Exception) {
            // Old line-format messages and malformed diagnostics do not provide a safe identity.
            null
        }
    }

    private fun MigrationHistoryVersion.toPublicState() = MigrationVersionState(
        versionId = id,
        tagId = tagId,
        status = status,
        message = message,
    )

    private fun MigrationCheckpointStatusAssessment.toRecoveryOperation(status: Int): MigrationRecoveryOperation =
        when (kind) {
            MigrationCheckpointStatusKind.COMPLETED -> MigrationRecoveryOperation.MIGRATE_DONE
            MigrationCheckpointStatusKind.UP_EXECUTION_RETRYABLE ->
                if (status == MigrationVersionState.STATUS_MIGRATE_STARTED) {
                    MigrationRecoveryOperation.START_EXECUTION
                } else {
                    MigrationRecoveryOperation.UP_EXECUTION
                }
            MigrationCheckpointStatusKind.DOWN_EXECUTION_RETRYABLE ->
                if (status == MigrationVersionState.STATUS_ROLLBACK_STARTED) {
                    MigrationRecoveryOperation.START_EXECUTION
                } else {
                    MigrationRecoveryOperation.DOWN_EXECUTION
                }
            MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE ->
                MigrationRecoveryOperation.UP_RECORDING_EXECUTION
            MigrationCheckpointStatusKind.DOWN_RECORDING_RETRYABLE ->
                MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION
            MigrationCheckpointStatusKind.UP_EXECUTION_FAILURE_NOT_RETRYABLE ->
                MigrationRecoveryOperation.UP_EXECUTION
            MigrationCheckpointStatusKind.DOWN_EXECUTION_FAILURE_NOT_RETRYABLE ->
                MigrationRecoveryOperation.DOWN_EXECUTION
            MigrationCheckpointStatusKind.UP_MANUAL_RECOVERY ->
                MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED
            MigrationCheckpointStatusKind.DOWN_MANUAL_RECOVERY ->
                MigrationRecoveryOperation.DOWN_MANUAL_RECOVERY_REQUIRED
            MigrationCheckpointStatusKind.INVALID -> MigrationRecoveryOperation.UNKNOWN
        }

    private fun <T> withMigrationLock(
        scope: OperationExecutionScope,
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }

        return try {
            gog.my_project.data_base_migration.executor.service.withMigrationLock(scope, block)
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            scope.failed(failure)
        }
    }

    private data class DiagnosticMigrationId(val value: String?)

    private data class ResolvedMigrationRecoveryTarget(
        val target: MigrationRecoveryTarget,
        val assessment: MigrationCheckpointStatusAssessment,
        val configuredVersionResolved: Boolean,
        val registeredMigrations: List<gog.my_project.data_base_migration.api.registration.item.RegisteredMigration>,
        val preparedVersion: PreparedMigrationVersion?,
    )

    private companion object {
        const val DIAGNOSTIC_SCHEMA_VERSION = 1
    }
}

/** Fresh, internally validated values for one Retry dispatch; never exposed as an authorization token. */
internal data class ValidatedMigrationRecoveryTarget(
    val target: MigrationRecoveryTarget,
    val status: Int,
    val checkpoint: MigrationRetryCheckpoint,
    val preparedVersion: PreparedMigrationVersion,
)
