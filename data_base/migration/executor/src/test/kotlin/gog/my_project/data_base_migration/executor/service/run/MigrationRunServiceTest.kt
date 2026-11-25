package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.SystemLock
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.controller.MigrationController
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryOperation
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import gog.my_project.data_base_migration.executor.service.result.MigrationExecutionResult
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTransactionService
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionStateService
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnostic
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnosticStage
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.version_history.MigrationHistoryEntry
import model.version_history.MigrationHistoryTag
import model.version_history.MigrationHistoryVersion
import model.version_history.MigrationVersionHistorySnapshot
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.ArrayDeque
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationRunServiceTest {
    private val service = MigrationRunService()
    private lateinit var database: FakeDatabase
    private lateinit var scope: OperationExecutionScope
    private val registration = RegisteredMigration(
        MigrationIdentity.fromValue(MigrationItem.value),
        SampleMigration::class,
    )

    @BeforeTest
    fun setUp() {
        SampleMigration.reset()
        PreparedMigrationTrace.reset()
        VersionMigrationTrace.reset()
        PreparedOrchestrationTrace.reset()
        database = FakeDatabase()
        scope = OperationExecutionScope(
            database.connection,
            DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
        )
    }

    @Test
    fun `preparation returns the registered migration and its runtime instance without running it`() {
        val preparedRegistration = RegisteredMigration(registration.id, PreparedInstanceMigration::class)

        val prepared = assertIs<PreparedMigration>(
            assertIs<ExecuteResult.Success<PreparedMigration>>(
                service.prepareMigration(scope, preparedRegistration),
            ).result,
        )

        assertSame(preparedRegistration, prepared.registration)
        assertSame<Migration>(requireNotNull(PreparedMigrationTrace.constructedInstance), prepared.instance)
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
        assertEquals(0, PreparedMigrationTrace.upCalls)
        assertEquals(0, PreparedMigrationTrace.downCalls)
        assertTrue(database.events.isEmpty())
    }

    @Test
    fun `preparation rejects a runtime identity that differs from its registration`() {
        val mismatchedRegistration = RegisteredMigration(
            MigrationIdentity.fromValue("different_migration"),
            PreparedInstanceMigration::class,
        )

        val failure = assertIs<ExecuteResult.Failure>(
            service.prepareMigration(scope, mismatchedRegistration),
        )

        assertIs<IllegalArgumentException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
        assertEquals(0, PreparedMigrationTrace.upCalls)
        assertEquals(0, PreparedMigrationTrace.downCalls)
        assertTrue(database.events.isEmpty())
    }

    @Test
    fun `preparation returns constructor failure before any prepared execution`() {
        val failingRegistration = RegisteredMigration(registration.id, ConstructorFailingMigration::class)

        val failure = assertIs<ExecuteResult.Failure>(
            service.prepareMigration(scope, failingRegistration),
        )

        assertSame(PreparationFailures.constructorFailure, failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(0, database.events.size)
    }

    @Test
    fun `preparation rethrows the original constructor Error`() {
        val fatalRegistration = RegisteredMigration(registration.id, FatalConstructorMigration::class)

        val thrown = assertFailsWith<AssertionError> {
            service.prepareMigration(scope, fatalRegistration)
        }

        assertSame(PreparationFailures.constructorFatal, thrown)
        assertSame(thrown, scope.fatalError)
        assertSame(thrown, scope.firstFailure)
        assertTrue(database.events.isEmpty())
    }

    @Test
    fun `prepared UP executes the same instance without constructing it again`() {
        val prepared = prepareObservableMigration()
        database.respond(emptyList(), emptyList(), emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, prepared)

        val success = assertIs<MigrationExecutionResult.Success>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.Direction.UP, success.direction)
        assertEquals(registration.id, success.migrationId)
        assertSame<Migration>(prepared.instance, requireNotNull(PreparedMigrationTrace.upInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
        assertEquals(1, PreparedMigrationTrace.upCalls)
        assertEquals(0, PreparedMigrationTrace.downCalls)
    }

    @Test
    fun `prepared DOWN executes the same instance without constructing it again`() {
        val prepared = prepareObservableMigration()
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
            emptyList(),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, prepared)

        val success = assertIs<MigrationExecutionResult.Success>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.Direction.DOWN, success.direction)
        assertEquals(registration.id, success.migrationId)
        assertSame<Migration>(prepared.instance, requireNotNull(PreparedMigrationTrace.downInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
        assertEquals(0, PreparedMigrationTrace.upCalls)
        assertEquals(1, PreparedMigrationTrace.downCalls)
    }

    @Test
    fun `prepared DOWN schema failure remains an EXECUTION failure`() {
        val prepared = prepareObservableMigration()
        database.failDdl = true
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, prepared)
        val failure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )

        assertEquals(MigrationExecutionResult.FailureStage.EXECUTION, failure.stage)
        assertSame<Migration>(prepared.instance, requireNotNull(PreparedMigrationTrace.downInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `prepared DOWN Error remains Fatal with the original reference`() {
        val prepared = prepareObservableMigration()
        val fatal = AssertionError("prepared down failed")
        PreparedMigrationTrace.downFailure = fatal
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, prepared)
        val fatalResult = assertIs<MigrationExecutionResult.Fatal>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )

        assertSame(fatal, fatalResult.error)
        assertSame(fatal, scope.fatalError)
        assertSame<Migration>(prepared.instance, requireNotNull(PreparedMigrationTrace.downInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `prepared UP schema failure remains an EXECUTION failure`() {
        val preparedUp = prepareObservableMigration()
        database.failDdl = true
        database.respond(emptyList())

        val upOutcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, preparedUp)
        val upFailure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(upOutcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.EXECUTION, upFailure.stage)
        assertSame<Migration>(preparedUp.instance, requireNotNull(PreparedMigrationTrace.upInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
    }

    @Test
    fun `prepared UP history failure remains a HISTORY_RECORD failure`() {
        val prepared = prepareObservableMigration()
        database.failDmlCalls += 2
        database.respond(emptyList(), emptyList(), emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, prepared)
        val failure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.HISTORY_RECORD, failure.stage)
        assertSame<Migration>(prepared.instance, requireNotNull(PreparedMigrationTrace.upInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
    }

    @Test
    fun `prepared DOWN history failure remains a HISTORY_RECORD failure`() {
        val preparedDown = prepareObservableMigration()
        database.failDmlCalls += 1
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val downOutcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, preparedDown)
        val downFailure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(downOutcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.HISTORY_RECORD, downFailure.stage)
        assertSame<Migration>(preparedDown.instance, requireNotNull(PreparedMigrationTrace.downInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
    }

    @Test
    fun `prepared Migration Error remains Fatal with the original reference`() {
        val prepared = prepareObservableMigration()
        val fatal = AssertionError("prepared migration failed")
        PreparedMigrationTrace.upFailure = fatal
        database.respond(emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, prepared)

        val fatalResult = assertIs<MigrationExecutionResult.Fatal>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertSame(fatal, fatalResult.error)
        assertSame(fatal, scope.fatalError)
        assertSame<Migration>(prepared.instance, requireNotNull(PreparedMigrationTrace.upInstance))
        assertEquals(1, PreparedMigrationTrace.constructorCalls)
    }

    private fun prepareObservableMigration(): PreparedMigration {
        val preparedRegistration = RegisteredMigration(registration.id, PreparedInstanceMigration::class)
        return assertIs<PreparedMigration>(
            assertIs<ExecuteResult.Success<PreparedMigration>>(
                service.prepareMigration(scope, preparedRegistration),
            ).result,
        )
    }

    @Test
    fun `runUp executes one migration then inserts tag and applied record in the supplied scope`() {
        database.respond(
            emptyList(), // Migration Tag is not present before up.
            emptyList(), // Migration Tag is still absent after up, so it is inserted.
            emptyList(), // No conflicting applied history exists.
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(
            listOf("read", "ddl", "read", "insert-tag", "read", "read", "insert-history"),
            database.events.map { it.substringBefore(':') },
        )
        val readSql = database.executedSql.filter { it.contains("SELECT", ignoreCase = true) }
        assertTrue(readSql.any { it.contains("system_migration_tag", ignoreCase = true) })
        assertTrue(readSql.any {
            it.contains("system_migration", ignoreCase = true) &&
                !it.contains("system_migration_tag", ignoreCase = true)
        })
        assertTrue(readSql.none { it.contains("WITH", ignoreCase = true) })
        assertTrue(database.executedSql.none { it.contains("system_migration_version", ignoreCase = true) })
        assertEquals(listOf(MigrationItem.value, MIGRATION_TAG_TYPE), database.readParameters[0])
        assertEquals(listOf(MigrationItem.value), database.readParameters[1])
        assertEquals(listOf(MigrationItem.value), database.readParameters[2])
        assertEquals(listOf(GENERATED_TAG_ID.toInt()), database.readParameters[3])
        assertEquals(listOf(MigrationItem.value, MIGRATION_TAG_TYPE), database.dmlCalls[0].parameters)
        assertEquals(listOf(GENERATED_TAG_ID.toInt(), VERSION_ID), database.dmlCalls[1].parameters)
        val historyInsert = database.executedSql.last { it.contains("INSERT", ignoreCase = true) &&
            it.contains("system_migration", ignoreCase = true) &&
            !it.contains("system_migration_tag", ignoreCase = true) }
        assertTrue(historyInsert.contains("tag_id", ignoreCase = true))
        assertTrue(historyInsert.contains("version_id", ignoreCase = true))
        // Every read, DDL, and DML statement is prepared through this one caller-owned Connection.
        assertTrue(database.connectionPreparationCount > 1)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(0, database.connectionCloseCount)
        assertTrue(database.statementCloseCount > 0)
        assertTrue(database.resultSetCloseCount > 0)
    }

    @Test
    fun `recordUpAttempt records metadata independently without invoking Schema`() {
        database.respond(emptyList(), emptyList())

        val outcome = service.recordUpAttempt(VersionTag, VERSION_ID, scope, registration)

        val success = assertIs<MigrationExecutionResult.Success>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.Direction.UP, success.direction)
        assertEquals(registration.id, success.migrationId)
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(
            listOf("read", "insert-tag", "read", "read", "insert-history"),
            database.events.map { it.substringBefore(':') },
        )
    }

    @Test
    fun `runUp reuses an existing item Tag with no applied history`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            emptyList(), // The existing Item Tag has no applied history yet.
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            emptyList(), // No conflicting history appeared while up() ran.
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(1, database.dmlCalls.size)
        val historyInsert = database.dmlCalls.single()
        assertTrue(historyInsert.sql.contains("INSERT", ignoreCase = true))
        assertTrue(historyInsert.sql.contains("system_migration", ignoreCase = true))
        assertTrue(!historyInsert.sql.contains("system_migration_tag", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID, VERSION_ID), historyInsert.parameters)
    }

    @Test
    fun `runUp attempt success includes Version and Migration identity`() {
        database.respond(emptyList(), emptyList(), emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, registration)

        val success = assertIs<MigrationExecutionResult.Success>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.Direction.UP, success.direction)
        assertSame(VersionTag, success.versionTag)
        assertEquals(VERSION_ID, success.versionRecordId)
        assertEquals(registration.id, success.migrationId)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(2, database.dmlCount)
    }

    @Test
    fun `runUp preflight failure is separate from a Migration attempt`() {
        database.failReadCalls += 1
        database.respond(emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, registration)

        val failure = assertIs<MigrationRunOutcome.PreflightFailure>(outcome).failure
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runUp rejects a failed Scope as a preflight failure without issuing queries`() {
        val failure = IllegalStateException("Scope is already failed")
        scope.failed<Unit>(failure)

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, registration)

        val preflight = assertIs<MigrationRunOutcome.PreflightFailure>(outcome).failure
        assertSame(failure, preflight.exception)
        assertEquals(0, SampleMigration.upCalls)
        assertTrue(database.events.isEmpty())
    }

    @Test
    fun `runUp attempt rethrows the original Error from an already Fatal Scope`() {
        val fatal = AssertionError("Scope is already fatal")
        scope.recordFatal(fatal)

        val thrown = assertFailsWith<AssertionError> {
            service.runUpAttempt(VersionTag, VERSION_ID, scope, registration)
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertSame(fatal, scope.firstFailure)
        assertEquals(0, SampleMigration.upCalls)
        assertTrue(database.events.isEmpty())

        val wrapperThrown = assertFailsWith<AssertionError> {
            service.runUp(VERSION_ID, scope, registration)
        }
        assertSame(fatal, wrapperThrown)
    }

    @Test
    fun `runUp callback failure is an EXECUTION attempt failure`() {
        val failure = IllegalArgumentException("up failed")
        SampleMigration.upFailure = failure
        database.respond(emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, registration)

        val attemptFailure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.EXECUTION, attemptFailure.stage)
        assertEquals(MigrationExecutionResult.Direction.UP, attemptFailure.direction)
        assertSame(VersionTag, attemptFailure.versionTag)
        assertEquals(VERSION_ID, attemptFailure.versionRecordId)
        assertEquals(registration.id, attemptFailure.migrationId)
        assertSame(failure, attemptFailure.cause)
        assertSame(failure, scope.firstFailure)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runUp history insert failure is a HISTORY_RECORD attempt failure`() {
        database.failDmlCalls += 2
        database.respond(emptyList(), emptyList(), emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, registration)

        val attemptFailure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.HISTORY_RECORD, attemptFailure.stage)
        assertEquals(MigrationExecutionResult.Direction.UP, attemptFailure.direction)
        assertSame(VersionTag, attemptFailure.versionTag)
        assertEquals(VERSION_ID, attemptFailure.versionRecordId)
        assertEquals(registration.id, attemptFailure.migrationId)
        assertIs<SQLException>(attemptFailure.cause)
        assertSame(attemptFailure.cause, scope.firstFailure)
        assertEquals(2, database.dmlCount)
        assertEquals(1, database.insertHistoryCount)
        assertEquals(1, SampleMigration.upCalls, "A recording failure must not execute Schema again")
    }

    @Test
    fun `runUp Error remains Fatal in internal result and legacy wrapper`() {
        val fatal = AssertionError("fatal up")
        SampleMigration.upFailure = fatal
        database.respond(emptyList())

        val outcome = service.runUpAttempt(VersionTag, VERSION_ID, scope, registration)

        val fatalResult = assertIs<MigrationExecutionResult.Fatal>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertSame(fatal, fatalResult.error)
        assertSame(fatal, scope.fatalError)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `legacy runUp wrapper rethrows the original Migration Error`() {
        val fatal = AssertionError("fatal up")
        SampleMigration.upFailure = fatal
        database.respond(emptyList())

        val thrown = assertFailsWith<AssertionError> {
            service.runUp(VERSION_ID, scope, registration)
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `failed up callback leaves migration history untouched`() {
        val failure = IllegalArgumentException("up failed")
        SampleMigration.upFailure = failure
        database.respond(emptyList())

        val result = service.runUp(VERSION_ID, scope, registration)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.connectionCloseCount)
    }

    @Test
    fun `failed schema DDL propagates and prevents tag and history writes`() {
        database.failDdl = true
        database.respond(emptyList())

        val result = service.runUp(VERSION_ID, scope, registration)

        assertIs<SQLException>(assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `failed tag insert prevents applied history insert and preserves JDBC failure`() {
        database.failDmlCalls += 1
        database.respond(
            emptyList(),
            emptyList(),
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(1, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("insert-history:") })
    }

    @Test
    fun `failed history insert is returned after successful tag creation`() {
        database.failDmlCalls += 2
        database.respond(
            emptyList(),
            emptyList(),
            emptyList(),
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertIs<SQLException>(assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(2, database.dmlCount)
        assertEquals(1, database.insertHistoryCount)
    }

    @Test
    fun `runUp rejects existing history without invoking the migration again`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown executes callback then removes only matching history and unreferenced item tag`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
            emptyList(),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.downCalls)
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(2, database.deleteCount)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
        assertTrue(database.executedSql.none { it.contains("system_migration_version", ignoreCase = true) })
        val historyDelete = database.dmlCalls.first { it.sql.contains("system_migration", ignoreCase = true) &&
            !it.sql.contains("system_migration_tag", ignoreCase = true) }
        assertTrue(historyDelete.sql.contains("tag_id", ignoreCase = true))
        assertTrue(historyDelete.sql.contains("version_id", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID, VERSION_ID), historyDelete.parameters)
        val tagDelete = database.dmlCalls.last()
        assertTrue(tagDelete.sql.contains("DELETE", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("system_migration_tag", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("id", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("WHERE", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagName", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagType", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID), tagDelete.parameters)
        assertTrue(database.events.indexOfFirst { it.startsWith("ddl:") } <
            database.events.indexOfFirst { it.startsWith("delete-history:") })
        assertTrue(database.events.indexOfFirst { it.startsWith("delete-history:") } <
            database.events.indexOfFirst { it.startsWith("delete-tag:") })
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(0, database.connectionCloseCount)
    }

    @Test
    fun `recordDownAttempt removes metadata independently without invoking Schema`() {
        database.respond(emptyList()) // No other Version still references the ITEM Tag.

        val outcome = service.recordDownAttempt(
            VersionTag,
            VERSION_ID,
            scope,
            registration,
            MigrationDownRecordingDecision.DeleteTargetHistory(MIGRATION_TAG_ID, VERSION_ID),
        )

        val success = assertIs<MigrationExecutionResult.Success>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.Direction.DOWN, success.direction)
        assertEquals(registration.id, success.migrationId)
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
        assertTrue(database.events.indexOfFirst { it.startsWith("delete-history:") } <
            database.events.indexOfFirst { it.startsWith("delete-tag:") })
    }

    @Test
    fun `DOWN recording reconciliation completes already deleted history and tag without Schema or DML`() {
        val snapshot = MigrationVersionHistorySnapshot(
            tags = listOf(
                MigrationHistoryTag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
            ),
            versions = listOf(
                MigrationHistoryVersion(
                    VERSION_ID,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_ROLLBACK_STARTED,
                ),
            ),
            migrations = emptyList<MigrationHistoryEntry>(),
        )
        val decision = MigrationDownRecordingReconciler().assess(snapshot, VERSION_ID, registration.id)

        assertEquals(MigrationDownRecordingDecision.Complete(retainedTagId = null), decision)
        val outcome = service.recordDownAttempt(VersionTag, VERSION_ID, scope, registration, decision)

        assertIs<MigrationExecutionResult.Success>(assertIs<MigrationRunOutcome.Attempt>(outcome).result)
        assertEquals(0, SampleMigration.downCalls)
        assertTrue(database.events.isEmpty())
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown attempt success includes Version and Migration identity`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
            emptyList(),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, registration)

        val success = assertIs<MigrationExecutionResult.Success>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.Direction.DOWN, success.direction)
        assertSame(VersionTag, success.versionTag)
        assertEquals(VERSION_ID, success.versionRecordId)
        assertEquals(registration.id, success.migrationId)
        assertEquals(1, SampleMigration.downCalls)
        assertEquals(2, database.dmlCount)
    }

    @Test
    fun `runDown preflight failure is separate from a Migration attempt`() {
        database.failReadCalls += 1
        database.respond(emptyList())

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, registration)

        val failure = assertIs<MigrationRunOutcome.PreflightFailure>(outcome).failure
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown callback failure is an EXECUTION attempt failure`() {
        val failure = IllegalStateException("down failed")
        SampleMigration.downFailure = failure
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, registration)

        val attemptFailure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.EXECUTION, attemptFailure.stage)
        assertEquals(MigrationExecutionResult.Direction.DOWN, attemptFailure.direction)
        assertSame(VersionTag, attemptFailure.versionTag)
        assertEquals(VERSION_ID, attemptFailure.versionRecordId)
        assertEquals(registration.id, attemptFailure.migrationId)
        assertSame(failure, attemptFailure.cause)
        assertSame(failure, scope.firstFailure)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown attempt rethrows the original Error from an already Fatal Scope`() {
        val fatal = AssertionError("Scope is already fatal")
        scope.recordFatal(fatal)

        val thrown = assertFailsWith<AssertionError> {
            service.runDownAttempt(VersionTag, VERSION_ID, scope, registration)
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertSame(fatal, scope.firstFailure)
        assertEquals(0, SampleMigration.downCalls)
        assertTrue(database.events.isEmpty())

        val wrapperThrown = assertFailsWith<AssertionError> {
            service.runDown(VERSION_ID, scope, registration)
        }
        assertSame(fatal, wrapperThrown)
    }

    @Test
    fun `runDown Error is Fatal and legacy wrapper rethrows the original Error`() {
        val fatal = AssertionError("fatal down")
        SampleMigration.downFailure = fatal
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val thrown = assertFailsWith<AssertionError> {
            service.runDown(VERSION_ID, scope, registration)
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown attempt returns Fatal with original Error and complete metadata`() {
        val fatal = AssertionError("fatal down")
        SampleMigration.downFailure = fatal
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, registration)

        val fatalResult = assertIs<MigrationExecutionResult.Fatal>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.Direction.DOWN, fatalResult.direction)
        assertSame(VersionTag, fatalResult.versionTag)
        assertEquals(VERSION_ID, fatalResult.versionRecordId)
        assertEquals(registration.id, fatalResult.migrationId)
        assertEquals(MigrationExecutionResult.FailureStage.EXECUTION, fatalResult.stage)
        assertSame(fatal, fatalResult.error)
        assertSame(fatal, scope.fatalError)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown history deletion failure is a HISTORY_RECORD attempt failure`() {
        database.failDmlCalls += 1
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, registration)

        val attemptFailure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.HISTORY_RECORD, attemptFailure.stage)
        assertEquals(MigrationExecutionResult.Direction.DOWN, attemptFailure.direction)
        assertSame(VersionTag, attemptFailure.versionTag)
        assertEquals(VERSION_ID, attemptFailure.versionRecordId)
        assertEquals(registration.id, attemptFailure.migrationId)
        assertIs<SQLException>(attemptFailure.cause)
        assertSame(attemptFailure.cause, scope.firstFailure)
        assertEquals(1, database.dmlCount)
        assertEquals(0, database.deleteTagCount)
        assertEquals(1, SampleMigration.downCalls, "A recording failure must not execute Schema again")
    }

    @Test
    fun `runDown item Tag deletion failure is a HISTORY_RECORD attempt failure`() {
        database.failDmlCalls += 2
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
            emptyList(),
        )

        val outcome = service.runDownAttempt(VersionTag, VERSION_ID, scope, registration)

        val attemptFailure = assertIs<MigrationExecutionResult.Failure>(
            assertIs<MigrationRunOutcome.Attempt>(outcome).result,
        )
        assertEquals(MigrationExecutionResult.FailureStage.HISTORY_RECORD, attemptFailure.stage)
        assertEquals(MigrationExecutionResult.Direction.DOWN, attemptFailure.direction)
        assertSame(VersionTag, attemptFailure.versionTag)
        assertEquals(VERSION_ID, attemptFailure.versionRecordId)
        assertEquals(registration.id, attemptFailure.migrationId)
        assertIs<SQLException>(attemptFailure.cause)
        assertSame(attemptFailure.cause, scope.firstFailure)
        assertEquals(2, database.dmlCount)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
    }

    @Test
    fun `runDown rejects missing applied history before invoking callback`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            emptyList(),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown rejects history recorded under a different version`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = OTHER_VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown rejects a tag of the wrong category before invoking callback`() {
        database.respond(rows(id = MIGRATION_TAG_ID, tagType = VERSION_TAG_TYPE))

        val result = service.runDown(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `failed down callback leaves history unchanged`() {
        val failure = IllegalStateException("down failed")
        SampleMigration.downFailure = failure
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `failed history deletion prevents tag deletion`() {
        database.failDmlCalls += 1
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(1, database.dmlCount)
        assertEquals(0, database.deleteTagCount)
    }

    @Test
    fun `failed tag deletion by id is returned and recorded in the scope`() {
        database.failDmlCalls += 2
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
            emptyList(),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        val tagDelete = database.dmlCalls.last()
        assertTrue(tagDelete.sql.contains("system_migration_tag", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("id", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagName", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagType", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID), tagDelete.parameters)
        assertTrue(database.executedSql.none { it.contains("system_migration_version", ignoreCase = true) })
    }

    @Test
    fun `runDown preserves item tag when another version still references it`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID, otherVersionId = OTHER_VERSION_ID),
            rows(id = OTHER_APPLIED_ID, versionId = OTHER_VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.downCalls)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(0, database.deleteTagCount)
    }

    @Test
    fun `runUp rejects non-positive version id without reading or executing`() {
        val result = service.runUp(0, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(database.executedSql.isEmpty())
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown rejects non-positive version id without reading or executing`() {
        val result = service.runDown(0, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(database.executedSql.isEmpty())
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `transactionUp executes a Version in declaration order on the supplied operation scope`() {
        VersionMigrationTrace.reset()
        database.respond(
            emptyList(), // Version Tag lookup.
            emptyList(), emptyList(), emptyList(), // First Migration history checks.
            emptyList(), emptyList(), emptyList(), // Second Migration history checks.
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration))
            .transactionUp(VersionTag)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first", "up:second"), VersionMigrationTrace.events)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(0, database.autoCommitRestoreCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionCloseCount)
        assertTrue(database.autoCommitAtPrepareStatement.all { it })
        assertEquals(listOf("close"), database.lifecycleEvents)
        assertEquals(1, database.dmlCalls.count {
            it.sql.contains("INSERT", true) && it.parameters == listOf(VersionTag.value, VERSION_TAG_TYPE)
        })
        assertTrue(database.executedSql.filter { it.contains("SELECT", true) }
            .none { it.contains("system_migration_version", true) })
        val versionInsert = database.dmlCalls.first { it.sql.contains("INSERT", true) &&
            it.sql.contains("system_migration_version", true) }
        val versionId = database.generatedIdForVersionInsert
        assertEquals(1000, versionInsert.parameters[0])
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, versionInsert.parameters[1])
        assertNull(versionInsert.parameters[2])
        val initialCheckpoint = MigrationRetryCheckpointCodec.decode(versionInsert.parameters[3] as String)
        assertEquals(MigrationRetryPhase.EXECUTION_PENDING, initialCheckpoint.phase)
        assertEquals(firstRegistration.id.value, initialCheckpoint.cursorMigrationId)
        val completionUpdate = database.dmlCalls.last()
        assertTrue(completionUpdate.sql.contains("UPDATE", true))
        assertTrue(completionUpdate.sql.contains("system_migration_version", true))
        assertEquals(STATUS_MIGRATE_COMPLETED, completionUpdate.parameters[0])
        assertEquals(versionId.toInt(), completionUpdate.parameters[1])
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, completionUpdate.parameters[2])
        assertTrue(completionUpdate.parameters[3] is String)
        assertEquals(1, database.dmlCalls.count {
            it.sql.contains("SET status = ?, retry_checkpoint = NULL", true)
        })
        val historyInserts = database.dmlCalls.filter { it.sql.contains("INSERT", true) &&
            it.sql.contains("version_id", true) }
        assertEquals(2, historyInserts.size)
        assertTrue(historyInserts.all { versionId.toInt() in it.parameters })
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[versionId.toInt()])
        assertNull(database.versionCheckpoints[versionId.toInt()])
    }

    @Test
    fun `UP persists in-flight and recording boundaries before advancing the ordered cursor`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration))
            .transactionUp(VersionTag)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        val versionId = database.generatedIdForVersionInsert.toInt()
        val phaseWrites = database.dmlCalls.filter {
            it.sql.contains("UPDATE", true) &&
                it.sql.contains("system_migration_version", true) &&
                it.sql.contains("SET retry_checkpoint = ?", true)
        }
        val persisted = phaseWrites.map { MigrationRetryCheckpointCodec.decode(it.parameters[0] as String) }
        assertEquals(
            listOf(
                MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                MigrationRetryPhase.RECORD_PENDING,
                MigrationRetryPhase.EXECUTION_PENDING,
                MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                MigrationRetryPhase.RECORD_PENDING,
                MigrationRetryPhase.FINALIZATION_PENDING,
            ),
            persisted.map { it.phase },
        )
        assertEquals(
            listOf(
                firstRegistration.id.value,
                firstRegistration.id.value,
                secondRegistration.id.value,
                secondRegistration.id.value,
                secondRegistration.id.value,
                null,
            ),
            persisted.map { it.cursorMigrationId },
        )
        val firstHistoryInsert = database.dmlCalls.indexOfFirst {
            it.sql.contains("INSERT", true) && it.sql.contains("version_id", true)
        }
        val firstCursorAdvance = database.dmlCalls.indexOf(phaseWrites[2])
        assertTrue(firstHistoryInsert >= 0 && firstHistoryInsert < firstCursorAdvance)
        assertEquals(STATUS_MIGRATE_COMPLETED, database.versionStatuses[versionId])
        assertNull(database.versionCheckpoints[versionId])
    }

    @Test
    fun `ambiguous UP Version insert never invokes Schema`() {
        database.failDmlWhen = { call ->
            if (call.sql.contains("INSERT", true) && call.sql.contains("system_migration_version", true)) {
                SQLException("Version insert acknowledgement lost")
            } else null
        }
        database.applyDmlBeforeFailure = { call ->
            call.sql.contains("INSERT", true) && call.sql.contains("system_migration_version", true)
        }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, SampleMigration.upCalls)
        assertTrue(database.events.none { it.startsWith("ddl:") })
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, database.versionStatuses[database.generatedIdForVersionInsert.toInt()])
        assertTrue(database.versionCheckpoints[database.generatedIdForVersionInsert.toInt()] is String)
    }

    @Test
    fun `transactionUp fails and leaves Version started when completion update count is not one`() {
        database.updateCountWhen = { call ->
            if (call.sql.contains("SET status = ?, retry_checkpoint = NULL", true)) 0 else null
        }
        database.respond(*emptyReadResponses())
        val configuration = migrationConfig { migrationVersion(VersionTagOne) { } }

        val result = versionService(configuration).transactionUp(VersionTagOne)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        val versionInsert = database.dmlCalls.first { it.sql.contains("INSERT", true) &&
            it.sql.contains("system_migration_version", true) }
        val completionUpdate = database.dmlCalls.last()
        assertTrue(completionUpdate.sql.contains("UPDATE", true))
        assertEquals(STATUS_MIGRATE_COMPLETED, completionUpdate.parameters[0])
        assertEquals(database.generatedIdForVersionInsert.toInt(), completionUpdate.parameters[1])
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertTrue(database.dmlCalls.none { it.sql.contains("DELETE", true) && it.sql.contains("system_migration_version", true) })
        assertTrue(versionInsert.sql.contains("system_migration_version", true))
    }

    @Test
    fun `transactionUp rejects a persisted Version before running its migrations`() {
        VersionMigrationTrace.reset()
        database.respond(
            rows(id = 51, tagType = VERSION_TAG_TYPE),
            rows(id = VERSION_ID, tagId = 51),
        )

        val result = versionService(versionConfiguration(firstRegistration))
            .transactionUp(VersionTag)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("already applied"))
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `transactionUp reports multiple persisted Versions for one Tag as inconsistent history`() {
        VersionMigrationTrace.reset()
        database.respond(
            rows(id = 51, tagType = VERSION_TAG_TYPE),
            listOf(
                mapOf("id" to VERSION_ID, "tagId" to 51),
                mapOf("id" to VERSION_ID + 1, "tagId" to 51),
            ),
        )

        val result = versionService(versionConfiguration(firstRegistration))
            .transactionUp(VersionTag)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("inconsistent"))
        assertTrue(failure.exception.message.orEmpty().contains("2 Versions"))
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionCloseCount)
    }

    @Test
    fun `transactionUp stops on a middle Migration failure and leaves Version started`() {
        VersionMigrationTrace.reset()
        database.failDdlCalls += 2
        database.respond(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(),
            emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration, thirdRegistration))
            .transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("up:first", "up:second"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(listOf("close"), database.lifecycleEvents)
        assertTrue(database.executedSql.none { it.contains("DELETE", true) &&
            it.contains("system_migration_version", true) })
        val checkpointWrites = database.dmlCalls.filter {
            it.sql.contains("UPDATE", true) && it.sql.contains("system_migration_version", true)
        }
        assertTrue(checkpointWrites.size >= 4)
        // This low-level service overload has no Failure Status Capability. Its durable
        // checkpoint still identifies the in-flight Migration without running later stages.
        assertTrue(failureStatusCalls().isEmpty())
        val latestCheckpointWrite = database.dmlCalls.last {
            it.sql.contains("UPDATE", true) && it.sql.contains("retry_checkpoint", true)
        }
        val checkpoint = MigrationRetryCheckpointCodec.decode(latestCheckpointWrite.parameters[0] as String)
        assertEquals(MigrationRetryPhase.EXECUTION_IN_FLIGHT, checkpoint.phase)
        assertEquals(secondRegistration.id.value, checkpoint.cursorMigrationId)
    }

    @Test
    fun `transactionUp rejects an invalid generated Version id before running migrations`() {
        VersionMigrationTrace.reset()
        database.nextGeneratedId = Int.MAX_VALUE.toLong()
        database.respond(emptyList(), emptyList())

        val result = versionService(versionConfiguration(firstRegistration)).transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertTrue(database.generatedIdForVersionInsert > Int.MAX_VALUE.toLong())
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `transactionUp propagates migration lookup failure after creating a new Version`() {
        VersionMigrationTrace.reset()
        database.failReadCalls += 2
        database.respond(emptyList())

        val result = versionService(versionConfiguration(firstRegistration)).transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(2, database.dmlCount)
        assertTrue(database.dmlCalls.any { it.sql.contains("system_migration_tag", true) })
        assertTrue(database.dmlCalls.any { it.sql.contains("system_migration_version", true) })
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `transactionDown marks Version started then runs reverse order and deletes Version last`() {
        VersionMigrationTrace.reset()
        database.respond(
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemSecond.value, MIGRATION_TAG_TYPE),
                tag(53, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51, status = STATUS_MIGRATE_COMPLETED),
            historyRows(history(61, 53, VERSION_ID), history(62, 52, VERSION_ID)),
            rows(id = 52, tagType = MIGRATION_TAG_TYPE), rows(id = 62, versionId = VERSION_ID), emptyList(),
            rows(id = 53, tagType = MIGRATION_TAG_TYPE), rows(id = 61, versionId = VERSION_ID), emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration))
            .transactionDown(VersionTag)

        assertEquals(
            true,
            assertIs<ExecuteResult.Success<Boolean>>(
                result,
                (result as? ExecuteResult.Failure)?.exception?.message,
            ).result,
        )
        assertEquals(listOf("down:second", "down:first"), VersionMigrationTrace.events)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(0, database.autoCommitRestoreCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertTrue(database.autoCommitAtPrepareStatement.all { it })
        val startUpdate = database.dmlCalls.first { it.sql.contains("UPDATE", true) }
        assertTrue(startUpdate.sql.contains("system_migration_version", true))
        assertEquals(STATUS_ROLLBACK_STARTED, startUpdate.parameters[0])
        assertTrue(startUpdate.parameters[1] is String)
        assertEquals(VERSION_ID, startUpdate.parameters[2])
        val initialDownCheckpoint = MigrationRetryCheckpointCodec.decode(startUpdate.parameters[1] as String)
        assertEquals(MigrationVersionState.STATUS_ROLLBACK_STARTED, initialDownCheckpoint.originalStatus)
        assertEquals(MigrationRetryPhase.EXECUTION_PENDING, initialDownCheckpoint.phase)
        assertEquals(secondRegistration.id.value, initialDownCheckpoint.cursorMigrationId)
        val startUpdateEvent = database.events.indexOfFirst {
            it.startsWith("dml:") && it.contains("UPDATE", true) && it.contains("system_migration_version", true)
        }
        val firstDdlEvent = database.events.indexOfFirst { it.startsWith("ddl:") }
        val firstHistoryDeleteEvent = database.events.indexOfFirst { it.startsWith("delete-history:") }
        assertTrue(startUpdateEvent >= 0 && startUpdateEvent < firstDdlEvent)
        assertTrue(firstDdlEvent < firstHistoryDeleteEvent)
        val historyDeletes = database.dmlCalls.filter { it.sql.contains("DELETE", true) &&
            it.sql.contains("version_id", true) }
        assertEquals(2, historyDeletes.size)
        assertTrue(historyDeletes.all { VERSION_ID in it.parameters })
        val versionDelete = database.dmlCalls.last()
        assertTrue(versionDelete.sql.contains("DELETE", true))
        assertTrue(versionDelete.sql.contains("system_migration_version", true))
        assertTrue(versionDelete.sql.contains("id", true))
        assertTrue(!versionDelete.sql.contains("tag_id", true))
        assertEquals(VERSION_ID, versionDelete.parameters[0])
        assertEquals(STATUS_ROLLBACK_STARTED, versionDelete.parameters[1])
        assertTrue(versionDelete.parameters[2] is String)
        assertNull(database.versionCheckpoints[VERSION_ID])
    }

    @Test
    fun `transactionDown stops before migrations when STARTED update count is not one`() {
        VersionMigrationTrace.reset()
        database.respondUpdateCounts(0)
        database.respond(
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51, status = STATUS_MIGRATE_COMPLETED),
            historyRows(history(61, 52, VERSION_ID)),
        )

        val result = versionService(versionConfiguration(firstRegistration)).transactionDown(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(1, database.dmlCalls.size)
        assertEquals(STATUS_ROLLBACK_STARTED, database.dmlCalls.single().parameters[0])
        assertEquals(VERSION_ID, database.dmlCalls.single().parameters[2])
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `transactionDown stops after a middle Migration failure and retains Version record`() {
        VersionMigrationTrace.reset()
        database.failDdlCalls += 2
        database.respond(
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
                tag(53, VersionItemSecond.value, MIGRATION_TAG_TYPE),
                tag(54, VersionItemThird.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51, status = STATUS_MIGRATE_COMPLETED),
            historyRows(
                history(61, 52, VERSION_ID),
                history(62, 53, VERSION_ID),
                history(63, 54, VERSION_ID),
            ),
            rows(id = 54, tagType = MIGRATION_TAG_TYPE), rows(id = 63, versionId = VERSION_ID), emptyList(),
            rows(id = 53, tagType = MIGRATION_TAG_TYPE), rows(id = 62, versionId = VERSION_ID),
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration, thirdRegistration))
            .transactionDown(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("down:third", "down:second"), VersionMigrationTrace.events)
        assertTrue(database.dmlCalls.none { it.sql.contains("DELETE", true) &&
            it.sql.contains("system_migration_version", true) })
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        val startUpdate = database.dmlCalls.first()
        assertEquals(STATUS_ROLLBACK_STARTED, startUpdate.parameters[0])
        assertEquals(VERSION_ID, startUpdate.parameters[2])
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, startUpdate.parameters[3])
    }

    @Test
    fun `transactionDown rejects a Version without persisted history`() {
        VersionMigrationTrace.reset()
        database.respond(
            tagRows(tag(51, VersionTag.value, VERSION_TAG_TYPE)),
            emptyList(),
            emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration)).transactionDown(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `transactionDown rolls back when deleting anything other than exactly one Version`() {
        VersionMigrationTrace.reset()
        database.updateCountWhen = { call ->
            if (call.sql.contains("DELETE", true) && call.sql.contains("system_migration_version", true)) 0 else null
        }
        database.respond(
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51, status = STATUS_MIGRATE_COMPLETED),
            historyRows(history(61, 52, VERSION_ID)),
            rows(id = 52, tagType = MIGRATION_TAG_TYPE), rows(id = 61, versionId = VERSION_ID), emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration)).transactionDown(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("down:first"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertTrue(database.dmlCalls.last().sql.contains("system_migration_version", true))
        val finalDelete = database.dmlCalls.last()
        assertEquals(VERSION_ID, finalDelete.parameters[0])
        assertEquals(STATUS_ROLLBACK_STARTED, finalDelete.parameters[1])
        assertEquals(
            MigrationRetryPhase.FINALIZATION_PENDING,
            MigrationRetryCheckpointCodec.decode(finalDelete.parameters[2] as String).phase,
        )
    }

    @Test
    fun `transactionUp reuses an existing Version Tag when no Version is applied`() {
        VersionMigrationTrace.reset()
        database.respond(
            versionTagRow(id = 51),
            emptyList(), // The existing Tag has no persisted Version.
            emptyList(), emptyList(), emptyList(), // Migration history checks.
        )

        val result = versionService(versionConfiguration(firstRegistration)).transactionUp(VersionTag)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first"), VersionMigrationTrace.events)
        assertEquals(1, database.dmlCalls.count {
            it.sql.contains("INSERT", true) && it.sql.contains("system_migration_version", true)
        })
        val completionUpdate = database.dmlCalls.last()
        assertTrue(completionUpdate.sql.contains("UPDATE", true))
        assertEquals(STATUS_MIGRATE_COMPLETED, completionUpdate.parameters[0])
        assertEquals(database.generatedIdForVersionInsert.toInt(), completionUpdate.parameters[1])
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, completionUpdate.parameters[2])
        assertEquals(MigrationRetryPhase.FINALIZATION_PENDING,
            MigrationRetryCheckpointCodec.decode(completionUpdate.parameters[3] as String).phase)
        assertTrue(database.dmlCalls.none {
            it.sql.contains("INSERT", true) && it.sql.contains("system_migration_tag", true) &&
                it.parameters.firstOrNull() == VersionTag.value
        })
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `transactionDown validates the complete history before running down`() {
        val cases = listOf(
            Triple("incomplete", listOf(history(61, 52, VERSION_ID)), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
                tag(53, VersionItemSecond.value, MIGRATION_TAG_TYPE),
            )),
            Triple("extra", listOf(
                history(61, 52, VERSION_ID),
                history(62, 54, VERSION_ID),
            ), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
                tag(54, "unconfigured_migration", MIGRATION_TAG_TYPE),
            )),
            Triple("duplicate", listOf(
                history(61, 52, VERSION_ID),
                history(62, 52, VERSION_ID),
            ), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            )),
            Triple("wrong version", listOf(history(61, 52, OTHER_VERSION_ID)), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            )),
        )

        cases.forEach { (caseName, historyRecords, tagRecords) ->
            setUp()
            VersionMigrationTrace.reset()
            val configuration = if (caseName == "incomplete") {
                versionConfiguration(firstRegistration, secondRegistration)
            } else {
                versionConfiguration(firstRegistration)
            }
            val tags = buildList {
                add(tag(51, VersionTag.value, VERSION_TAG_TYPE))
                addAll(tagRecords)
                if (caseName == "wrong version") add(tag(55, "other_version", VERSION_TAG_TYPE))
            }
            val versions = if (caseName == "wrong version") {
                listOf(
                    mapOf("id" to VERSION_ID, "tagId" to 51, "status" to STATUS_MIGRATE_COMPLETED),
                    mapOf("id" to OTHER_VERSION_ID, "tagId" to 55, "status" to STATUS_MIGRATE_COMPLETED),
                )
            } else {
                listOf(mapOf("id" to VERSION_ID, "tagId" to 51, "status" to STATUS_MIGRATE_COMPLETED))
            }
            database.respond(
                tagRows(*tags.toTypedArray()),
                versions,
                historyRows(*historyRecords.toTypedArray()),
            )
            val result = versionService(configuration).transactionDown(VersionTag)

            val failure = assertIs<ExecuteResult.Failure>(result, "$caseName: ${(result as? ExecuteResult.Failure)?.exception?.message}")
            val expectedFailureText = when (caseName) {
                "incomplete" -> "history rows"
                "extra" -> "not present in the configuration"
                "duplicate" -> "Duplicate applied Migration history"
                else -> "different persisted Version"
            }
            assertTrue(
                failure.exception.message.orEmpty().contains(expectedFailureText, ignoreCase = true),
                "$caseName: ${failure.exception.message}",
            )
            assertTrue(VersionMigrationTrace.events.isEmpty(), caseName)
            assertTrue(database.dmlCalls.isEmpty(), caseName)
            assertEquals(0, database.commitCount, caseName)
            assertEquals(0, database.rollbackCount, caseName)
        }
    }

    @Test
    fun `transactionDown retains Version Tag and allows applying that tag again after rollback`() {
        VersionMigrationTrace.reset()
        database.respond(
            // Rollback reads a complete snapshot, then runDown reads the selected Migration rows.
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51, status = STATUS_MIGRATE_COMPLETED),
            historyRows(history(61, 52, VERSION_ID)),
            rows(id = 52, tagType = MIGRATION_TAG_TYPE),
            rows(id = 61, versionId = VERSION_ID),
            emptyList(),
            // Reapply under the retained Version Tag. No Version row is currently applied.
            versionTagRow(id = 51),
            emptyList(),
            emptyList(), emptyList(), emptyList(),
        )

        val service = versionService(versionConfiguration(firstRegistration))
        val down = service.transactionDown(VersionTag)
        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(down).result)

        val up = service.transactionUp(VersionTag)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(up).result)
        assertEquals(listOf("down:first", "up:first"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(2, database.dmlCalls.count {
            it.sql.contains("system_migration_version", true) && it.sql.contains("DELETE", true)
        } + database.dmlCalls.count {
            it.sql.contains("system_migration_version", true) && it.sql.contains("INSERT", true)
        })
        assertTrue(database.dmlCalls.none {
            it.sql.contains("INSERT", true) && it.sql.contains("system_migration_tag", true) &&
                it.parameters.firstOrNull() == VersionTag.value
        })
        val versionDelete = database.dmlCalls.first { it.sql.contains("DELETE", true) &&
            it.sql.contains("system_migration_version", true) }
        assertEquals(VERSION_ID, versionDelete.parameters[0])
        assertEquals(STATUS_ROLLBACK_STARTED, versionDelete.parameters[1])
    }

    @Test
    fun `transaction preparation failure is reported before executing on the supplied scope`() {
        VersionMigrationTrace.reset()

        val result = versionService(versionConfiguration(firstRegistration, versionName = "another_version"))
            .transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(database.executedSql.isEmpty())
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(1, database.connectionCloseCount)
    }

    @Test
    fun `version transaction rethrows original Error from an already Fatal Scope`() {
        val fatal = AssertionError("Scope is already fatal")
        scope.recordFatal(fatal)
        val service = MigrationVersionTransactionService(versionConfiguration(firstRegistration))

        val thrown = assertFailsWith<AssertionError> {
            service.transactionUp(scope, VersionTag)
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertSame(fatal, scope.firstFailure)
        assertTrue(database.events.isEmpty())

        val rollbackThrown = assertFailsWith<AssertionError> {
            service.transactionDown(scope, VersionTag)
        }
        assertSame(fatal, rollbackThrown)
        assertTrue(database.events.isEmpty())
    }

    @Test
    fun `orchestration rethrows original Error from an already Fatal Scope`() {
        val fatal = AssertionError("Scope is already fatal")
        scope.recordFatal(fatal)
        val controller = MigrationController(orchestrationConfiguration())

        val thrown = assertFailsWith<AssertionError> {
            controller.migrate(scope)
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertSame(fatal, scope.firstFailure)
        assertTrue(database.events.isEmpty())

        val rollbackThrown = assertFailsWith<AssertionError> {
            controller.rollback(scope)
        }
        assertSame(fatal, rollbackThrown)
        assertTrue(database.events.isEmpty())
    }

    @Test
    fun `migrate runs all pending Versions in ascending order on the supplied scope`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.migrate(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(
            listOf("up:first", "up:second", "up:third", "up:fourth", "up:fifth"),
            VersionMigrationTrace.events,
        )
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(23, database.dmlCalls.count { it.sql.contains("system_migration_version", true) })
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(0, database.autoCommitRestoreCount)
        assertEquals(1, database.connectionCloseCount)
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
        val events = database.events
        val firstRead = events.indexOfFirst { it.startsWith("read:") }
        val lastVersionUpdate = events.indexOfLast {
            it.startsWith("dml:") && it.contains("UPDATE", true) && it.contains("system_migration_version", true)
        }
        assertTrue(events.indexOf("lock-acquire") < firstRead)
        assertTrue(lastVersionUpdate < events.indexOf("lock-release"))
        assertTrue(events.indexOf("lock-release") < events.indexOf("connection-close"))
    }

    @Test
    fun `migrate preflights every selected Version tag before writing the first marker`() {
        VersionMigrationTrace.reset()
        val firstVersion = migrationConfig {
            migrationVersion(VersionTagOne) { migration<VersionFirstMigration>() }
        }.registrations.single()
        val configuration = MigrationConfiguration(
            listOf(
                firstVersion,
                MigrationVersion("tagless_version", listOf(SingleMigration(thirdRegistration))),
            ),
        )
        database.respond(*emptyReadResponses())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("no valid MigrationVersionTag"))
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertTrue(database.executedSql.none { it.contains("CREATE TABLE", ignoreCase = true) })
        assertEquals(3, database.events.count { it.startsWith("read:") })
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `migrate prepares every selected Migration before the first Version marker and reuses each instance`() {
        val configuration = migrationConfig {
            migrationVersion(PreparedVersionOne) { migration<PreparedFirstMigration>() }
            migrationVersion(PreparedVersionTwo) { migration<PreparedSecondMigration>() }
        }
        // Configuration registration resolves identity by constructing each type once; count only
        // the distinct runtime instances prepared by the orchestration path below.
        PreparedOrchestrationTrace.reset()
        database.onVersionWrite = { PreparedOrchestrationTrace.events += "marker" }
        database.respond(*emptyReadResponses())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        val firstMarker = PreparedOrchestrationTrace.events.indexOf("marker")
        assertTrue(firstMarker >= 0)
        assertTrue(PreparedOrchestrationTrace.events.indexOf("constructor:first") < firstMarker)
        assertTrue(PreparedOrchestrationTrace.events.indexOf("constructor:second") < firstMarker)
        assertEquals(1, PreparedOrchestrationTrace.events.count { it == "constructor:first" })
        assertEquals(1, PreparedOrchestrationTrace.events.count { it == "constructor:second" })
        assertEquals(
            listOf("up:first", "up:second"),
            PreparedOrchestrationTrace.events.filter { it.startsWith("up:") },
        )
        assertSame(
            PreparedOrchestrationTrace.constructedInstances.getValue("first"),
            PreparedOrchestrationTrace.executedInstances.getValue("first"),
        )
        assertSame(
            PreparedOrchestrationTrace.constructedInstances.getValue("second"),
            PreparedOrchestrationTrace.executedInstances.getValue("second"),
        )
    }

    @Test
    fun `UP execution failure writes status 101 under the original lock and Connection`() {
        database.failDdlCalls += 1
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("DDL 1 failed"))
        val write = failureStatusCalls().single()
        assertStatusTransition(
            write,
            target = MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
            versionId = database.generatedIdForVersionInsert.toInt(),
            expected = MigrationVersionState.STATUS_MIGRATE_STARTED,
            expectedStage = "UP_EXECUTION",
            messageContains = "DDL 1 failed",
            migrationId = "run_one_migration",
            checkpointPhase = MigrationRetryPhase.EXECUTION_IN_FLIGHT,
        )
        val writeIndex = database.dmlCalls.indexOf(write)
        assertTrue(database.lockHeldAtDmlCall[writeIndex], "Failure status write must remain inside the held lock callback")
        assertEquals(1, database.connectionBuildCount, "The write must use the original Operation Connection")
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionCloseCount)
    }

    @Test
    fun `failed EXECUTION_IN_FLIGHT CAS prevents UP Schema dispatch`() {
        database.updateCountWhen = { call ->
            if (call.sql.contains("SET retry_checkpoint = ?", true) &&
                MigrationRetryCheckpointCodec.decode(call.parameters[0] as String).phase ==
                MigrationRetryPhase.EXECUTION_IN_FLIGHT
            ) 0 else null
        }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.upCalls)
        assertTrue(database.events.none { it.startsWith("ddl:") })
        val write = failureStatusCalls().single()
        assertEquals(MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED, write.parameters[0])
        val checkpoint = MigrationRetryCheckpointCodec.decode(write.parameters[2] as String)
        assertEquals(MigrationRetryPhase.EXECUTION_PENDING, checkpoint.phase)
    }

    @Test
    fun `failed RECORD_PENDING CAS records UP Recording failure without repeating Schema`() {
        database.updateCountWhen = { call ->
            if (call.sql.contains("SET retry_checkpoint = ?", true) &&
                MigrationRetryCheckpointCodec.decode(call.parameters[0] as String).phase ==
                MigrationRetryPhase.RECORD_PENDING
            ) 0 else null
        }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, SampleMigration.upCalls)
        assertTrue(database.events.any { it.startsWith("ddl:") })
        assertEquals(0, database.insertHistoryCount)
        val write = failureStatusCalls().single()
        assertEquals(MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, write.parameters[0])
        val checkpoint = MigrationRetryCheckpointCodec.decode(write.parameters[2] as String)
        assertEquals(MigrationRetryPhase.RECORD_PENDING, checkpoint.phase)
    }

    @Test
    fun `failed cursor advancement after UP Recording does not execute the next Migration`() {
        database.updateCountWhen = { call ->
            if (call.sql.contains("SET retry_checkpoint = ?", true)) {
                val checkpoint = MigrationRetryCheckpointCodec.decode(call.parameters[0] as String)
                if (checkpoint.phase == MigrationRetryPhase.EXECUTION_PENDING &&
                    checkpoint.cursorMigrationId == secondRegistration.id.value
                ) 0 else null
            } else null
        }
        database.respond(*emptyReadResponses())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(migrationConfig {
                migrationVersion(VersionTag) {
                    migration<VersionFirstMigration>()
                    migration<VersionSecondMigration>()
                }
            }).migrate(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(
            listOf("up:first"),
            VersionMigrationTrace.events,
        )
        assertEquals(1, database.insertHistoryCount)
        val write = failureStatusCalls().single()
        assertEquals(MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, write.parameters[0])
        val checkpoint = MigrationRetryCheckpointCodec.decode(write.parameters[2] as String)
        assertEquals(MigrationRetryPhase.RECORD_PENDING, checkpoint.phase)
        assertEquals(firstRegistration.id.value, checkpoint.cursorMigrationId)
    }

    @Test
    fun `UP SQL exception diagnostic includes SQLState and vendor error code`() {
        val sqlFailure = SQLException("Duplicate column name display_name", "42S21", 1060)
        SampleMigration.upFailure = sqlFailure
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(result).exception)
        val diagnostic = Json.parseToJsonElement(failureStatusCalls().single().parameters[1] as String).jsonObject
        assertEquals(1, diagnostic.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("UP_EXECUTION", diagnostic.getValue("stage").jsonPrimitive.content)
        assertEquals(database.generatedIdForVersionInsert.toInt(), diagnostic.getValue("versionId").jsonPrimitive.int)
        assertEquals("java.sql.SQLException", diagnostic.getValue("exception").jsonPrimitive.content)
        assertEquals("42S21", diagnostic.getValue("sqlState").jsonPrimitive.content)
        assertEquals(1060, diagnostic.getValue("errorCode").jsonPrimitive.int)
        assertEquals(registration.id.value, diagnostic.getValue("migrationId").jsonPrimitive.content)
        assertEquals("Duplicate column name display_name", diagnostic.getValue("message").jsonPrimitive.content)
        val failureWrite = failureStatusCalls().single()
        assertEquals(MigrationVersionState.STATUS_MIGRATE_FAILED_UP, failureWrite.parameters[0])
        assertEquals(
            MigrationRetryPhase.EXECUTION_IN_FLIGHT,
            MigrationRetryCheckpointCodec.decode(failureWrite.parameters[2] as String).phase,
            "A known Schema callback failure remains non-retryable despite being recorded as status 101",
        )
    }

    @Test
    fun `UP cross Version History inconsistency after Schema requires manual recovery status 110`() {
        val responses = emptyReadResponses(count = 7).toMutableList()
        responses += listOf(history(APPLIED_ID, MIGRATION_TAG_ID, OTHER_VERSION_ID))
        database.respond(*responses.toTypedArray())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(1, database.events.count { it.startsWith("ddl:") })
        val write = failureStatusCalls().single()
        assertStatusTransition(
            write,
            target = MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED,
            versionId = database.generatedIdForVersionInsert.toInt(),
            expected = MigrationVersionState.STATUS_MIGRATE_STARTED,
            expectedStage = "UP_RECORDING",
            messageContains = "conflicting applied history",
            migrationId = registration.id.value,
            checkpointPhase = MigrationRetryPhase.RECORD_PENDING,
        )
    }

    @Test
    fun `UP recording boundary CAS failure after successful Schema is status 102 and persists RECORD_PENDING`() {
        database.updateCountWhen = { call ->
            if (call.sql.contains("SET retry_checkpoint = ?", true) &&
                MigrationRetryCheckpointCodec.decode(call.parameters[0] as String).phase ==
                MigrationRetryPhase.RECORD_PENDING
            ) 0 else null
        }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(1, database.events.count { it.startsWith("ddl:") })
        assertEquals(0, database.insertHistoryCount)
        val write = failureStatusCalls().single()
        assertStatusTransition(
            write,
            target = MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            versionId = database.generatedIdForVersionInsert.toInt(),
            expected = MigrationVersionState.STATUS_MIGRATE_STARTED,
            expectedStage = "UP_RECORDING",
            messageContains = "Checkpoint compare-and-set was not applied",
            migrationId = registration.id.value,
            checkpointPhase = MigrationRetryPhase.RECORD_PENDING,
        )
    }

    @Test
    fun `diagnostic message accessor failure uses safe JSON and preserves original Migration failure`() {
        val metadataFailure = IllegalStateException("message accessor failed")
        val migrationFailure = ThrowingMessageException(metadataFailure)
        SampleMigration.upFailure = migrationFailure
        database.respond(*emptyReadResponses())
        var operationScope: OperationExecutionScope? = null

        val result = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            operationScope = activeScope
            MigrationController(sampleConfiguration()).migrate(activeScope)
        }

        assertSame(migrationFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(migrationFailure, operationScope?.firstFailure)
        assertEquals(1, failureStatusCalls().size, "Diagnostic failure must not retry the status UPDATE")
        val json = Json.parseToJsonElement(failureStatusCalls().single().parameters[1] as String).jsonObject
        assertEquals("UP_EXECUTION", json.getValue("stage").jsonPrimitive.content)
        assertEquals(ThrowingMessageException::class.java.name, json.getValue("exception").jsonPrimitive.content)
        assertEquals(registration.id.value, json.getValue("migrationId").jsonPrimitive.content)
        assertEquals("", json.getValue("message").jsonPrimitive.content)
    }

    @Test
    fun `ordinary diagnostic construction failure remains secondary to the Migration failure`() {
        val metadataFailure = IllegalStateException("message accessor failed")
        val migrationFailure = ThrowingMessageException(metadataFailure)
        val laterFatal = AssertionError("verify retained secondary failure")
        SampleMigration.upFailure = migrationFailure
        database.respond(*emptyReadResponses())
        var activeScope: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor(versionTestContext()).connection { scope ->
                activeScope = scope
                val migrationResult = MigrationController(sampleConfiguration()).migrate(scope)
                assertSame(migrationFailure, scope.firstFailure)
                scope.recordFatal(laterFatal)
                migrationResult
            }
        }

        assertSame(laterFatal, thrown)
        assertSame(migrationFailure, activeScope?.firstFailure)
        assertTrue(laterFatal.suppressed.any { it === migrationFailure })
        assertTrue(laterFatal.suppressed.any { it === metadataFailure })
        assertEquals(1, failureStatusCalls().size)
    }

    @Test
    fun `SQLException metadata accessor failure uses safe JSON and preserves original Migration failure`() {
        val metadataFailure = IllegalStateException("SQLState accessor failed")
        val migrationFailure = ThrowingSqlStateException(metadataFailure)
        SampleMigration.upFailure = migrationFailure
        database.respond(*emptyReadResponses())
        var operationScope: OperationExecutionScope? = null

        val result = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            operationScope = activeScope
            MigrationController(sampleConfiguration()).migrate(activeScope)
        }

        assertSame(migrationFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(migrationFailure, operationScope?.firstFailure)
        assertEquals(1, failureStatusCalls().size, "Diagnostic failure must not retry the status UPDATE")
        val json = Json.parseToJsonElement(failureStatusCalls().single().parameters[1] as String).jsonObject
        assertEquals("UP_EXECUTION", json.getValue("stage").jsonPrimitive.content)
        assertEquals(ThrowingSqlStateException::class.java.name, json.getValue("exception").jsonPrimitive.content)
        assertEquals(registration.id.value, json.getValue("migrationId").jsonPrimitive.content)
        assertEquals(null, json.getValue("sqlState").jsonPrimitive.contentOrNull)
        assertEquals(null, json.getValue("errorCode").jsonPrimitive.intOrNull)
        assertEquals("", json.getValue("message").jsonPrimitive.content)
    }

    @Test
    fun `fatal diagnostic metadata failure keeps exact Error identity and skips status write`() {
        val fatal = AssertionError("fatal message accessor failure")
        val migrationFailure = ThrowingMessageException(fatal)
        SampleMigration.upFailure = migrationFailure
        database.respond(*emptyReadResponses())

        val thrown = assertFailsWith<AssertionError> {
            executeSampleMigration()
        }

        assertSame(fatal, thrown)
        assertTrue(fatal.suppressed.any { it === migrationFailure })
        assertTrue(failureStatusCalls().isEmpty(), "Fatal diagnostic construction must bypass status recording")
    }

    @Test
    fun `diagnostic Migration ID validation matches MigrationIdentity grammar`() {
        val candidates = listOf(
            "a",
            "migration2",
            "create_manager_customers",
            "a".repeat(255),
            "",
            "_migration",
            "Migration",
            "migration_",
            "migration__item",
            "migration-item",
            "a".repeat(256),
            "create_users\nmalicious=true",
        )

        candidates.forEach { candidate ->
            val identityAccepted = runCatching { MigrationIdentity.fromValue(candidate) }.isSuccess
            val diagnostic = MigrationFailureDiagnostic(
                stage = MigrationFailureDiagnosticStage.UP_EXECUTION,
                exceptionClassName = "java.lang.IllegalStateException",
                sqlState = null,
                vendorErrorCode = null,
                message = null,
                migrationId = candidate,
            )
            assertEquals(
                if (identityAccepted) candidate else null,
                diagnostic.migrationId,
                "Diagnostic validation diverged for '$candidate'",
            )
        }
    }

    @Test
    fun `UP history recording failure writes status 102 and preserves RECORD_PENDING`() {
        database.failDmlWhen = { call ->
            if (call.sql.contains("INSERT", true) &&
                call.sql.contains("system_migration", true) &&
                !call.sql.contains("system_migration_tag", true) &&
                !call.sql.contains("system_migration_version", true)
            ) SQLException("Migration history insert failed") else null
        }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertEquals("Migration history insert failed", failure.message)
        assertTrue(database.events.any { it.startsWith("ddl:") }, "UP must succeed before history recording fails")
        assertEquals(1, database.events.count { it.startsWith("ddl:") }, "UP Schema must run exactly once")
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            versionId = database.generatedIdForVersionInsert.toInt(),
            expected = MigrationVersionState.STATUS_MIGRATE_STARTED,
            expectedStage = "UP_RECORDING",
            messageContains = "Migration history insert failed",
            migrationId = "run_one_migration",
            checkpointPhase = MigrationRetryPhase.RECORD_PENDING,
        )
        assertTrue(database.dmlCalls.none {
            isVersionStatusUpdate(it) && it.parameters.firstOrNull() == STATUS_MIGRATE_COMPLETED
        }, "UP finalization must not run after Recording fails")
    }

    @Test
    fun `UP metadata recording resumes existing item Tag after partial failure without rerunning Schema`() {
        database.failDmlWhen = { call ->
            if (isHistoryInsert(call)) SQLException("Migration history insert failed") else null
        }
        database.respond(*emptyReadResponses())

        val initialResult = executeSampleMigration()
        val initialFailure = assertIs<ExecuteResult.Failure>(initialResult).exception
        assertEquals("Migration history insert failed", initialFailure.message)
        assertEquals(1, SampleMigration.upCalls)

        val versionId = database.generatedIdForVersionInsert.toInt()
        assertEquals(MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, database.versionStatuses[versionId])
        val checkpointBeforeRecording = MigrationRetryCheckpointCodec.decode(
            requireNotNull(database.versionCheckpoints[versionId]),
        )
        assertEquals(MigrationRetryPhase.RECORD_PENDING, checkpointBeforeRecording.phase)

        val itemTagInsertsBeforeRecording = database.dmlCalls.count(::isItemTagInsert)
        val writesBeforeRecording = database.dmlCalls.size
        database.failDmlWhen = null

        val resumedOutcome = service.recordUpAttempt(VersionTag, versionId, scope, registration)
        val resumedResult = assertIs<MigrationExecutionResult.Success>(
            assertIs<MigrationRunOutcome.Attempt>(resumedOutcome).result,
        )
        assertEquals(versionId, resumedResult.versionRecordId)
        assertEquals(registration.id, resumedResult.migrationId)

        val resumedWrites = database.dmlCalls.drop(writesBeforeRecording)
        assertEquals(1, resumedWrites.size, "Recording continuation should write only the missing History row")
        assertTrue(isHistoryInsert(resumedWrites.single()), resumedWrites.single().sql)
        assertEquals(itemTagInsertsBeforeRecording, database.dmlCalls.count(::isItemTagInsert))
        assertEquals(1, itemTagInsertsBeforeRecording, "The valid ITEM Tag created before failure must be reused")
        assertEquals(1, SampleMigration.upCalls, "Metadata-only recording must not invoke Schema again")
        assertEquals(1, database.events.count { it.startsWith("ddl:") })

        fun assertReconciliationRejected(vararg reads: List<Map<String, Any?>>) {
            val conflictingDatabase = FakeDatabase()
            val conflictingScope = OperationExecutionScope(
                conflictingDatabase.connection,
                DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
            )
            conflictingDatabase.respond(*reads)

            val outcome = service.recordUpAttempt(VersionTag, versionId, conflictingScope, registration)
            val failure = assertIs<MigrationExecutionResult.Failure>(
                assertIs<MigrationRunOutcome.Attempt>(outcome).result,
            )
            assertEquals(MigrationExecutionResult.FailureStage.RECONCILIATION, failure.stage)
            assertSame(failure.cause, conflictingScope.firstFailure)
            assertEquals(0, conflictingDatabase.dmlCount, "Contradictory evidence must fail before any write")
        }

        assertReconciliationRejected(
            tagRows(
                tag(MIGRATION_TAG_ID, MigrationItem.value, MIGRATION_TAG_TYPE),
                tag(MIGRATION_TAG_ID + 1, MigrationItem.value, MIGRATION_TAG_TYPE),
            ),
        )
        assertReconciliationRejected(
            tagRows(tag(MIGRATION_TAG_ID, MigrationItem.value, VERSION_TAG_TYPE)),
        )
        assertReconciliationRejected(
            tagRows(tag(MIGRATION_TAG_ID, MigrationItem.value, MIGRATION_TAG_TYPE)),
            historyRows(history(APPLIED_ID, MIGRATION_TAG_ID, OTHER_VERSION_ID)),
        )
        assertReconciliationRejected(
            tagRows(tag(MIGRATION_TAG_ID, MigrationItem.value, MIGRATION_TAG_TYPE)),
            historyRows(
                history(APPLIED_ID, MIGRATION_TAG_ID, versionId),
                history(OTHER_APPLIED_ID, MIGRATION_TAG_ID, versionId),
            ),
        )
        assertEquals(1, SampleMigration.upCalls, "Reconciliation checks must never invoke Schema")
    }

    @Test
    fun `UP completion status update failure writes status 102 and preserves FINALIZATION_PENDING`() {
        database.updateCountWhen = { call ->
            if (call.sql.contains("SET status = ?, retry_checkpoint = NULL", true)) 0
            else null
        }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("Could not finalize UP Migration Version"), failure.message)
        assertEquals(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            database.versionStatuses[database.generatedIdForVersionInsert.toInt()],
        )
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            versionId = database.generatedIdForVersionInsert.toInt(),
            expected = MigrationVersionState.STATUS_MIGRATE_STARTED,
            expectedStage = "UP_RECORDING",
            messageContains = "Could not finalize UP Migration Version",
            migrationId = null,
            checkpointPhase = MigrationRetryPhase.FINALIZATION_PENDING,
        )
    }

    @Test
    fun `DOWN execution failure writes status 201`() {
        database.failDdlCalls += 1
        val history = orchestrationHistory(appliedVersionCount = 1)
        respondForRollback(history, listOf(VersionTagOne.value))

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).rollback(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("DDL 1 failed"))
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN,
            versionId = VERSION_ID_ONE,
            expected = MigrationVersionState.STATUS_ROLLBACK_STARTED,
            expectedStage = "DOWN_EXECUTION",
            messageContains = "DDL 1 failed",
            migrationId = VersionItemSecond.value,
            checkpointPhase = MigrationRetryPhase.EXECUTION_IN_FLIGHT,
        )
    }

    @Test
    fun `DOWN recording boundary CAS failure after successful Schema is status 202 and persists RECORD_PENDING`() {
        database.updateCountWhen = { call ->
            if (call.sql.contains("SET retry_checkpoint = ?", true) &&
                MigrationRetryCheckpointCodec.decode(call.parameters[0] as String).phase ==
                MigrationRetryPhase.RECORD_PENDING
            ) 0 else null
        }
        val history = orchestrationHistory(appliedVersionCount = 1)
        respondForRollback(history, listOf(VersionTagOne.value))

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).rollback(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, database.events.count { it.startsWith("ddl:") })
        assertEquals(0, database.deleteHistoryCount)
        val write = failureStatusCalls().single()
        assertStatusTransition(
            write,
            target = MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
            versionId = VERSION_ID_ONE,
            expected = MigrationVersionState.STATUS_ROLLBACK_STARTED,
            expectedStage = "DOWN_RECORDING",
            messageContains = "Checkpoint compare-and-set was not applied",
            migrationId = VersionItemSecond.value,
            checkpointPhase = MigrationRetryPhase.RECORD_PENDING,
        )
    }

    @Test
    fun `DOWN history recording failure writes status 202 and preserves RECORD_PENDING`() {
        database.failDmlWhen = { call ->
            if (call.sql.contains("DELETE", true) &&
                call.sql.contains("system_migration", true) &&
                !call.sql.contains("system_migration_tag", true) &&
                !call.sql.contains("system_migration_version", true)
            ) SQLException("Migration history delete failed") else null
        }
        val history = orchestrationHistory(appliedVersionCount = 1)
        respondForRollback(history, listOf(VersionTagOne.value))

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).rollback(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertEquals("Migration history delete failed", failure.message)
        assertEquals(1, database.events.count { it.startsWith("ddl:") }, "DOWN Schema must run exactly once")
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
            versionId = VERSION_ID_ONE,
            expected = MigrationVersionState.STATUS_ROLLBACK_STARTED,
            expectedStage = "DOWN_RECORDING",
            messageContains = "Migration history delete failed",
            migrationId = VersionItemSecond.value,
            checkpointPhase = MigrationRetryPhase.RECORD_PENDING,
        )
        assertTrue(database.dmlCalls.none {
            it.sql.contains("DELETE", true) && it.sql.contains("system_migration_version", true)
        }, "DOWN finalization must not delete the Version after Recording fails")
    }

    @Test
    fun `DOWN Version deletion failure writes status 202 without recreating the Version`() {
        database.failDmlWhen = { call ->
            if (isVersionDelete(call)) SQLException("Version delete acknowledgement failed") else null
        }
        database.applyDmlBeforeFailure = ::isVersionDelete
        val history = orchestrationHistory(appliedVersionCount = 1)
        respondForRollback(history, listOf(VersionTagOne.value))

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).rollback(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertEquals("Version delete acknowledgement failed", failure.message)
        assertEquals(null, database.versionStatuses[VERSION_ID_ONE], "The simulated committed delete must remain deleted")
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
            versionId = VERSION_ID_ONE,
            expected = MigrationVersionState.STATUS_ROLLBACK_STARTED,
            expectedStage = "DOWN_RECORDING",
            messageContains = "Version delete acknowledgement failed",
            migrationId = null,
            checkpointPhase = MigrationRetryPhase.FINALIZATION_PENDING,
        )
        assertTrue(database.dmlCalls.none {
            it.sql.contains("INSERT", true) && it.sql.contains("system_migration_version", true)
        }, "Failure recording must never recreate the Version row")
    }

    @Test
    fun `UP preflight failure after Version insertion does not write a failure status`() {
        database.failReadCalls += 5 // Three orchestration reads, one Version Tag lookup, then UP attempt preflight.
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            database.versionStatuses[database.generatedIdForVersionInsert.toInt()],
        )
        assertTrue(failureStatusCalls().isEmpty())
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `DOWN preflight failure after status 200 does not write failure status or execute down`() {
        val tags = listOf(
            tag(VERSION_TAG_ID_ONE, VersionTagOne.value, VERSION_TAG_TYPE),
            tag(MIGRATION_TAG_ID, MigrationItem.value, MIGRATION_TAG_TYPE),
        )
        val versions = listOf(mapOf("id" to VERSION_ID_ONE, "tagId" to VERSION_TAG_ID_ONE, "status" to STATUS_MIGRATE_COMPLETED))
        val histories = listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE))
        database.respond(tags, versions, histories, tags, versions, histories, emptyList())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(sampleConfiguration()).rollback(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(MigrationVersionState.STATUS_ROLLBACK_STARTED, database.versionStatuses[VERSION_ID_ONE])
        assertTrue(failureStatusCalls().isEmpty())
        assertEquals(1, database.dmlCalls.count { isVersionStatusUpdate(it) })
        assertEquals(0, SampleMigration.downCalls)
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `invalid UP Version id does not write a failure status`() {
        database.nextGeneratedId = Int.MAX_VALUE.toLong()
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(database.generatedIdForVersionInsert > Int.MAX_VALUE.toLong())
        assertTrue(failureStatusCalls().isEmpty())
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `unconfirmed DOWN status 200 does not write failure status or execute down`() {
        database.respondUpdateCounts(0)
        val history = singleAppliedSampleHistory()
        database.respond(history.tags, history.versions, history.migrations, history.tags, history.versions, history.migrations)

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(sampleConfiguration()).rollback(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(failureStatusCalls().isEmpty())
        assertEquals(0, SampleMigration.downCalls)
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `fatal Migration Error bypasses ordinary failure status recording`() {
        val fatal = AssertionError("fatal Migration up")
        SampleMigration.upFailure = fatal
        database.respond(*emptyReadResponses())

        val thrown = assertFailsWith<AssertionError> { executeSampleMigration() }

        assertSame(fatal, thrown)
        assertTrue(failureStatusCalls().isEmpty())
    }

    @Test
    fun `fatal Error from failure status write preserves exact Fatal identity and original ordinary failure`() {
        val original = SQLException("Migration DDL failed")
        val fatal = AssertionError("failure status write failed fatally")
        SampleMigration.upFailure = original
        database.failDmlWhen = { call -> if (isFailureStatusWrite(call)) fatal else null }
        database.respond(*emptyReadResponses())
        var activeScope: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor(versionTestContext()).connection { scope ->
                activeScope = scope
                MigrationController(sampleConfiguration()).migrate(scope)
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, activeScope?.fatalError)
        assertSame(original, activeScope?.firstFailure)
        assertTrue(fatal.suppressed.any { it === original })
        assertEquals(1, failureStatusCalls().size)
    }

    @Test
    fun `status write exception remains suppressed under the original Migration failure`() {
        val original = SQLException("Migration DDL failed")
        val statusFailure = SQLException("status UPDATE failed")
        SampleMigration.upFailure = original
        database.failDmlWhen = { call -> if (isFailureStatusWrite(call)) statusFailure else null }
        database.respond(*emptyReadResponses())
        var activeScope: OperationExecutionScope? = null

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            activeScope = scope
            MigrationController(sampleConfiguration()).migrate(scope)
        }

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(original, activeScope?.firstFailure)
        assertTrue(original.suppressed.any { it === statusFailure })
        assertEquals(1, failureStatusCalls().size)
    }

    @Test
    fun `NOT_APPLIED failure status result does not trigger another write`() {
        SampleMigration.upFailure = SQLException("Migration DDL failed")
        database.updateCountWhen = { call -> if (isFailureStatusWrite(call)) 0 else null }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, failureStatusCalls().size)
    }

    @Test
    fun `INDETERMINATE failure status result does not trigger another write`() {
        SampleMigration.upFailure = SQLException("Migration DDL failed")
        database.updateCountWhen = { call -> if (isFailureStatusWrite(call)) 2 else null }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, failureStatusCalls().size)
    }

    @Test
    fun `late UP recording write cannot overwrite a Version already marked completed`() {
        val completionFailure = SQLException("completion acknowledgement lost")
        database.failDmlWhen = { call ->
            if (call.sql.contains("SET status = ?, retry_checkpoint = NULL", true)) {
                completionFailure
            } else null
        }
        database.applyDmlBeforeFailure = { call ->
            call.sql.contains("SET status = ?, retry_checkpoint = NULL", true)
        }
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertSame(completionFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(STATUS_MIGRATE_COMPLETED, database.versionStatuses[database.generatedIdForVersionInsert.toInt()])
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            versionId = database.generatedIdForVersionInsert.toInt(),
            expected = MigrationVersionState.STATUS_MIGRATE_STARTED,
            expectedStage = "UP_RECORDING",
            messageContains = "completion acknowledgement lost",
            migrationId = null,
        )
        assertEquals(0, database.dmlUpdateCounts.last())
    }

    @Test
    fun `failure status write remains before Migration lock release and uses one Operation Connection`() {
        database.failDdlCalls += 1
        database.respond(*emptyReadResponses())

        val result = executeSampleMigration()

        assertIs<ExecuteResult.Failure>(result)
        val statusWrite = failureStatusCalls().single()
        val writeDmlIndex = database.dmlCalls.indexOf(statusWrite)
        assertTrue(database.lockHeldAtDmlCall[writeDmlIndex])
        assertEquals(1, database.connectionBuildCount)
        val writeEventIndex = database.events.indexOf("dml:${statusWrite.sql}")
        assertTrue(database.events.indexOf("lock-acquire") < writeEventIndex)
        assertTrue(writeEventIndex < database.events.indexOf("lock-release"))
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
    }

    @Test
    fun `constructor failure in the last selected Version prevents all markers and Migration execution`() {
        val configuration = migrationConfig {
            migrationVersion(PreparedVersionOne) { migration<PreparedFirstMigration>() }
            migrationVersion(PreparedVersionTwo) { migration<PreparedFailingMigration>() }
        }
        PreparedOrchestrationTrace.reset()
        PreparedOrchestrationTrace.failingConstructorEnabled = true
        database.onVersionWrite = { PreparedOrchestrationTrace.events += "marker" }
        database.respond(*emptyReadResponses())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(PreparedOrchestrationTrace.constructorFailure, failure)
        assertEquals(
            listOf("constructor:first", "constructor:failing"),
            PreparedOrchestrationTrace.events,
        )
        assertEquals(0, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("ddl:") })
        assertTrue(PreparedOrchestrationTrace.executedInstances.isEmpty())
    }

    @Test
    fun `rollback prepares all selected Migrations and reuses the same instances in reverse order`() {
        val configuration = migrationConfig {
            migrationVersion(PreparedVersionOne) {
                migration<PreparedFirstMigration>()
                migration<PreparedSecondMigration>()
            }
        }
        PreparedOrchestrationTrace.reset()
        database.onVersionWrite = { PreparedOrchestrationTrace.events += "marker" }
        val tags = listOf(
            tag(501, PreparedVersionOne.value, VERSION_TAG_TYPE),
            tag(601, PreparedFirstItem.value, MIGRATION_TAG_TYPE),
            tag(602, PreparedSecondItem.value, MIGRATION_TAG_TYPE),
        )
        val versions = listOf(
            mapOf("id" to 701, "tagId" to 501, "status" to STATUS_MIGRATE_COMPLETED),
        )
        val migrations = listOf(
            history(801, 601, 701),
            history(802, 602, 701),
        )
        database.respond(
            tags, versions, migrations, // Orchestrator History Snapshot.
            tags, versions, migrations, // Live Version History validation.
            rows(id = 602, tagType = MIGRATION_TAG_TYPE),
            rows(id = 802, versionId = 701),
            emptyList(),
            rows(id = 601, tagType = MIGRATION_TAG_TYPE),
            rows(id = 801, versionId = 701),
            emptyList(),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).rollback(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        val firstMarker = PreparedOrchestrationTrace.events.indexOf("marker")
        assertTrue(firstMarker >= 0)
        assertTrue(PreparedOrchestrationTrace.events.indexOf("constructor:first") < firstMarker)
        assertTrue(PreparedOrchestrationTrace.events.indexOf("constructor:second") < firstMarker)
        assertEquals(
            listOf("down:second", "down:first"),
            PreparedOrchestrationTrace.events.filter { it.startsWith("down:") },
        )
        assertEquals(1, PreparedOrchestrationTrace.events.count { it == "constructor:first" })
        assertEquals(1, PreparedOrchestrationTrace.events.count { it == "constructor:second" })
        assertSame(
            PreparedOrchestrationTrace.constructedInstances.getValue("first"),
            PreparedOrchestrationTrace.executedInstances.getValue("first"),
        )
        assertSame(
            PreparedOrchestrationTrace.constructedInstances.getValue("second"),
            PreparedOrchestrationTrace.executedInstances.getValue("second"),
        )
        assertEquals(1, database.dmlCalls.count { it.sql.contains("DELETE", true) && it.sql.contains("system_migration_version", true) })
    }

    @Test
    fun `migrate to Version includes the target and executes no later Version`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.migrate(VersionTagTwo, scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first", "up:second", "up:third"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(13, database.dmlCalls.count { it.sql.contains("system_migration_version", true) })
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `all four orchestration commands fail closed without identity before reading History`() {
        val target = object : MigrationVersionTag { override val value = "unknown_target" }
        val controller = MigrationController(MigrationConfiguration(emptyList()))
        val commands: List<(OperationExecutionScope) -> ExecuteResult<Boolean>> = listOf(
            controller::migrate,
            { scope -> controller.migrate(target, scope) },
            controller::rollback,
            { scope -> controller.rollback(target, scope) },
        )

        commands.forEach { command ->
            database = FakeDatabase()
            val result = OperationExecutionExecutor(versionTestContext(withIdentity = false)).connection(command)

            val failure = assertIs<ExecuteResult.Failure>(result).exception
            assertTrue(failure.message.orEmpty().contains("DatabaseIdentity"))
            assertTrue(database.events.none { it.startsWith("read:") || it == "lock-acquire" })
            assertEquals(0, database.commitCount)
            assertEquals(0, database.rollbackCount)
        }
    }

    @Test
    fun `lock timeout prevents History reads and does not attempt release`() {
        database.systemLockAcquireValue = 0

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(MigrationConfiguration(emptyList())).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("Timed out acquiring session advisory lock"))
        assertEquals(listOf("lock-acquire", "connection-close"), database.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `acquire SQL failure prevents History reads and does not attempt release`() {
        val acquireFailure = SQLException("GET_LOCK failed")
        database.systemLockAcquireFailure = acquireFailure

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(MigrationConfiguration(emptyList())).migrate(scope)
        }

        assertSame(acquireFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("lock-acquire", "connection-close"), database.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `lock release failure is returned after the full orchestration callback`() {
        database.systemLockReleaseValue = 0
        database.respond(*emptyReadResponses())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(MigrationConfiguration(emptyList())).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("RELEASE_LOCK did not own"))
        assertEquals(3, database.events.count { it.startsWith("read:") })
        assertTrue(database.events.indexOf("lock-release") < database.events.indexOf("connection-close"))
    }

    @Test
    fun `callback failure remains primary when orchestration lock release fails`() {
        database.systemLockReleaseValue = 0
        database.respond(*emptyReadResponses())
        val configuration = MigrationConfiguration(
            listOf(MigrationVersion("tagless_version", listOf(SingleMigration(firstRegistration)))),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("no valid MigrationVersionTag"))
        assertEquals(1, failure.suppressed.size)
        assertTrue(failure.suppressed.single().message.orEmpty().contains("RELEASE_LOCK did not own"))
        assertTrue(database.events.indexOf("lock-release") < database.events.indexOf("connection-close"))
    }

    @Test
    fun `fatal Migration Error releases system lock before Operation closes Connection`() {
        val fatal = AssertionError("fatal migration callback")
        SampleMigration.upFailure = fatal
        database.respond(*emptyReadResponses())
        val configuration = migrationConfig {
            migrationVersion(VersionTagOne) { migration<SampleMigration>() }
        }

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor(versionTestContext()).connection { scope ->
                MigrationController(configuration).migrate(scope)
            }
        }

        assertSame(fatal, thrown)
        assertEquals(0, database.rollbackCount)
        assertTrue(database.events.indexOf("lock-acquire") < database.events.indexOf("lock-release"))
        assertTrue(database.events.indexOf("lock-release") < database.events.indexOf("connection-close"))
    }

    @Test
    fun `rollback runs only latest applied Version and reverses Migration declaration order`() {
        VersionMigrationTrace.reset()
        val history = orchestrationHistory(appliedVersionCount = 3)
        respondForRollback(history, listOf(VersionTagThree.value))
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.rollback(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:fifth", "down:fourth"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        val startUpdates = database.dmlCalls.filter {
            it.sql.contains("UPDATE", true) && it.sql.contains("system_migration_version", true) &&
                it.sql.contains("retry_checkpoint IS NULL", true)
        }
        assertEquals(1, startUpdates.size)
        assertEquals(STATUS_ROLLBACK_STARTED, startUpdates.single().parameters[0])
        assertEquals(VERSION_ID_THREE, startUpdates.single().parameters[2])
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, startUpdates.single().parameters[3])
        val versionDeletes = database.dmlCalls.filter {
            it.sql.contains("DELETE", true) && it.sql.contains("system_migration_version", true)
        }
        assertEquals(1, versionDeletes.size)
        assertEquals(listOf(VERSION_ID_THREE), versionDeletes.map { it.parameters[0] })
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `rollback to target excludes target and traverses applied Versions descending`() {
        VersionMigrationTrace.reset()
        val history = orchestrationHistory(appliedVersionCount = 3)
        respondForRollback(history, listOf(VersionTagThree.value, VersionTagTwo.value))
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.rollback(VersionTagOne, scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:fifth", "down:fourth", "down:third"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        val startUpdates = database.dmlCalls.filter {
            it.sql.contains("UPDATE", true) && it.sql.contains("system_migration_version", true) &&
                it.sql.contains("retry_checkpoint IS NULL", true)
        }
        assertEquals(2, startUpdates.size)
        assertEquals(listOf(VERSION_ID_THREE, VERSION_ID_TWO), startUpdates.map { it.parameters[2] })
        val versionDeletes = database.dmlCalls.filter {
            it.sql.contains("DELETE", true) && it.sql.contains("system_migration_version", true)
        }
        assertEquals(2, versionDeletes.size)
        assertEquals(listOf(VERSION_ID_THREE, VERSION_ID_TWO), versionDeletes.map { it.parameters[0] })
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `failed later Version leaves it started and keeps earlier Version completed`() {
        VersionMigrationTrace.reset()
        database.failDdlCalls += 2
        database.respond(*emptyReadResponses())
        val configuration = migrationConfig {
            migrationVersion(VersionTagOne) { migration<VersionFirstMigration>() }
            migrationVersion(VersionTagTwo) { migration<VersionThirdMigration>() }
        }

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("up:first", "up:third"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(0, database.autoCommitRestoreCount)
        val statusUpdates = database.dmlCalls.filter {
            it.sql.contains("SET status = ?, retry_checkpoint = NULL", true)
        }
        assertEquals(1, statusUpdates.size)
        assertEquals(STATUS_MIGRATE_COMPLETED, statusUpdates.single().parameters.first())
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
            versionId = database.generatedIdForVersionInsert.toInt(),
            expected = MigrationVersionState.STATUS_MIGRATE_STARTED,
            expectedStage = "UP_EXECUTION",
            migrationId = VersionItemThird.value,
        )
    }

    @Test
    fun `migrate stops before writes when any persisted Version has incomplete status`() {
        val history = withVersionStatus(
            orchestrationHistory(appliedVersionCount = 2),
            VERSION_ID_ONE,
            status = MigrationVersionState.STATUS_MIGRATE_STARTED,
        )
        val controller = MigrationController(orchestrationConfiguration())

        assertIncompleteVersionBlocks(history, expectedStatus = MigrationVersionState.STATUS_MIGRATE_STARTED) { scope ->
            controller.migrate(scope)
        }
    }

    @Test
    fun `migrate to target checks incomplete persisted Versions outside the selected range`() {
        val history = withVersionStatus(
            orchestrationHistory(appliedVersionCount = 1),
            VERSION_ID_ONE,
            status = STATUS_ROLLBACK_STARTED,
        )
        val controller = MigrationController(orchestrationConfiguration())

        // v1 is already applied and lies before the pending v2-v3 selection.
        assertIncompleteVersionBlocks(history, expectedStatus = STATUS_ROLLBACK_STARTED) { scope ->
            controller.migrate(VersionTagThree, scope)
        }
    }

    @Test
    fun `rollback checks incomplete persisted Versions outside the latest selected Version`() {
        val history = withVersionStatus(
            orchestrationHistory(appliedVersionCount = 3),
            VERSION_ID_TWO,
            status = MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
        )
        val controller = MigrationController(orchestrationConfiguration())

        // rollback() would select only v3; the incomplete v2 must still stop the command.
        assertIncompleteVersionBlocks(history, expectedStatus = MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD) { scope ->
            controller.rollback(scope)
        }
    }

    @Test
    fun `rollback to target checks incomplete target Version excluded from down selection`() {
        val history = withVersionStatus(
            orchestrationHistory(appliedVersionCount = 2),
            VERSION_ID_ONE,
            status = STATUS_ROLLBACK_STARTED,
        )
        val controller = MigrationController(orchestrationConfiguration())

        // rollback(v1) selects v2 for DOWN and excludes v1, but the global gate still checks v1.
        assertIncompleteVersionBlocks(history, expectedStatus = STATUS_ROLLBACK_STARTED) { scope ->
            controller.rollback(VersionTagOne, scope)
        }
    }

    @Test
    fun `manual recovery statuses block every normal migrate and rollback entry point`() {
        val controller = MigrationController(orchestrationConfiguration())
        val manualStatuses = listOf(
            MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED,
            MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED,
        )

        manualStatuses.forEach { status ->
            database = FakeDatabase()
            assertIncompleteVersionBlocks(
                withVersionStatus(orchestrationHistory(appliedVersionCount = 2), VERSION_ID_ONE, status),
                expectedStatus = status,
            ) { scope -> controller.migrate(scope) }
            database = FakeDatabase()
            assertIncompleteVersionBlocks(
                withVersionStatus(orchestrationHistory(appliedVersionCount = 1), VERSION_ID_ONE, status),
                expectedStatus = status,
            ) { scope -> controller.migrate(VersionTagThree, scope) }
            database = FakeDatabase()
            assertIncompleteVersionBlocks(
                withVersionStatus(orchestrationHistory(appliedVersionCount = 3), VERSION_ID_TWO, status),
                expectedStatus = status,
            ) { scope -> controller.rollback(scope) }
            database = FakeDatabase()
            assertIncompleteVersionBlocks(
                withVersionStatus(orchestrationHistory(appliedVersionCount = 2), VERSION_ID_ONE, status),
                expectedStatus = status,
            ) { scope -> controller.rollback(VersionTagOne, scope) }
        }
    }

    @Test
    fun `unknown target and invalid history fail before any Version executes`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val orchestration = MigrationController(orchestrationConfiguration())
        val unknownTarget = object : MigrationVersionTag { override val value = "not_registered" }

        val unknownResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.migrate(unknownTarget, scope)
        }

        assertIs<ExecuteResult.Failure>(unknownResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)

        val unknownRollbackResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.rollback(unknownTarget, scope)
        }
        assertIs<ExecuteResult.Failure>(unknownRollbackResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)

        setUp()
        val unknownPersistedTag = mapOf("id" to 900, "tagName" to "removed_version", "tagType" to VERSION_TAG_TYPE)
        database.respond(
            listOf(unknownPersistedTag),
            listOf(mapOf("id" to 901, "tagId" to 900)),
            emptyList(),
        )
        val invalidHistoryResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).migrate(scope)
        }

        assertIs<ExecuteResult.Failure>(invalidHistoryResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)

        database.respond(
            listOf(unknownPersistedTag),
            listOf(mapOf("id" to 901, "tagId" to 900)),
            emptyList(),
        )
        val invalidHistoryRollbackResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).rollback(scope)
        }
        assertIs<ExecuteResult.Failure>(invalidHistoryRollbackResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `unsupported DOWN fails the Version transaction instead of skipping it`() {
        VersionMigrationTrace.reset()
        val configuration = migrationConfig {
            migrationVersion(VersionTagOne) { migration<UnsupportedDownMigration>() }
        }
        val tag = mapOf("id" to 51, "tagName" to VersionTagOne.value, "tagType" to VERSION_TAG_TYPE)
        val item = mapOf("id" to 71, "tagName" to UnsupportedDownItem.value, "tagType" to MIGRATION_TAG_TYPE)
        val version = mapOf("id" to 101, "tagId" to 51, "status" to STATUS_MIGRATE_COMPLETED)
        val applied = mapOf("id" to 801, "tagId" to 71, "versionId" to 101)
        database.respond(
            listOf(tag, item), listOf(version), listOf(applied), // Current-version validation.
            listOf(tag, item), listOf(version), listOf(applied), // transactionDown history validation.
            listOf(mapOf("id" to 71, "tagType" to MIGRATION_TAG_TYPE)),
            listOf(mapOf("id" to 801, "versionId" to 101)),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).rollback(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<UnsupportedOperationException>(failure.exception)
        assertEquals(listOf("down:unsupported"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertTrue(database.dmlCalls.none { it.sql.contains("DELETE", true) })
        val startUpdate = database.dmlCalls.first {
            it.sql.contains("retry_checkpoint IS NULL", true)
        }
        assertEquals(STATUS_ROLLBACK_STARTED, startUpdate.parameters[0])
        assertEquals(101, startUpdate.parameters[2])
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN,
            versionId = 101,
            expected = MigrationVersionState.STATUS_ROLLBACK_STARTED,
            expectedStage = "DOWN_EXECUTION",
            migrationId = UnsupportedDownItem.value,
        )
    }

    @Test
    fun `missing selected Version tag fails before starting any Version transaction`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val configuration = MigrationConfiguration(
            listOf(MigrationVersion("tagless_version", listOf(SingleMigration(firstRegistration)))),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("no valid MigrationVersionTag"))
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `repeated migrate and rollback are idempotent for an applied Version`() {
        VersionMigrationTrace.reset()
        // Match the fake generated IDs from the preceding real UP execution:
        // Version Tag=1000, Version=1001, ITEM Tag=1002, History=1003.
        val history = OrchestrationHistory(
            tags = listOf(
                tag(1000, VersionTagOne.value, VERSION_TAG_TYPE),
                tag(1002, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            versions = listOf(
                mapOf("id" to 1001, "tagId" to 1000, "status" to STATUS_MIGRATE_COMPLETED),
            ),
            migrations = listOf(history(1003, 1002, 1001)),
        )
        database.respond(
            emptyList(), emptyList(), emptyList(), // Initial history.
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), // Initial Version up, including Tag confirmation.
            history.tags, history.versions, history.migrations, // Repeated migrate preflight.
            history.tags, history.versions, history.migrations, // Rollback preflight.
            history.tags, history.versions, history.migrations, // Version transactionDown validation.
            listOf(mapOf("id" to 1002, "tagType" to MIGRATION_TAG_TYPE)),
            listOf(mapOf("id" to 1003, "tagId" to 1002, "versionId" to 1001)),
            emptyList(),
            history.tags.take(1), emptyList(), emptyList(), // Repeated rollback preflight after rollback.
        )
        val orchestration = MigrationController(
            migrationConfig { migrationVersion(VersionTagOne) { migration<VersionFirstMigration>() } },
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.migrate(scope)).result)
            assertEquals(8, database.dmlCount)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.migrate(scope)).result)
            assertEquals(8, database.dmlCount)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.rollback(scope)).result)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.rollback(scope)).result)
            ExecuteResult.Success(true)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first", "down:first"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `empty Version is persisted and marked completed instead of silently skipped`() {
        database.respond(*emptyReadResponses())
        val configuration = migrationConfig { migrationVersion(VersionTagOne) { } }

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(0, database.commitCount)
        assertEquals(2, database.dmlCalls.count { it.sql.contains("system_migration_version", true) })
        val versionUpdate = database.dmlCalls.single { it.sql.contains("UPDATE", true) }
        assertEquals(STATUS_MIGRATE_COMPLETED, versionUpdate.parameters[0])
        assertEquals(database.generatedIdForVersionInsert.toInt(), versionUpdate.parameters[1])
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, versionUpdate.parameters[2])
        assertEquals(MigrationRetryPhase.FINALIZATION_PENDING,
            MigrationRetryCheckpointCodec.decode(versionUpdate.parameters[3] as String).phase)
        assertEquals(0, database.executedSql.count { it.contains("CREATE TABLE", true) })
    }

    private fun orchestrationConfiguration() = migrationConfig {
        migrationVersion(VersionTagOne) {
            migration<VersionFirstMigration>()
            migration<VersionSecondMigration>()
        }
        migrationVersion(VersionTagTwo) { migration<VersionThirdMigration>() }
        migrationVersion(VersionTagThree) {
            migration<VersionFourthMigration>()
            migration<VersionFifthMigration>()
        }
        migrationVersion(VersionTagFour) { }
    }

    private fun emptyReadResponses(count: Int = 100): Array<List<Map<String, Any?>>> =
        Array(count) { emptyList() }

    private fun orchestrationHistory(appliedVersionCount: Int): OrchestrationHistory {
        val definitions = listOf(
            Triple(VersionTagOne, VERSION_TAG_ID_ONE, VERSION_ID_ONE) to listOf(
                VersionItemFirst.value to ITEM_TAG_ID_FIRST,
                VersionItemSecond.value to ITEM_TAG_ID_SECOND,
            ),
            Triple(VersionTagTwo, VERSION_TAG_ID_TWO, VERSION_ID_TWO) to listOf(
                VersionItemThird.value to ITEM_TAG_ID_THIRD,
            ),
            Triple(VersionTagThree, VERSION_TAG_ID_THREE, VERSION_ID_THREE) to listOf(
                VersionItemFourth.value to ITEM_TAG_ID_FOURTH,
                VersionItemFifth.value to ITEM_TAG_ID_FIFTH,
            ),
            Triple(VersionTagFour, VERSION_TAG_ID_FOUR, VERSION_ID_FOUR) to emptyList(),
        )
        val tags = definitions.map { (version, _) ->
            mapOf("id" to version.second, "tagName" to version.first.value, "tagType" to VERSION_TAG_TYPE)
        } + definitions.take(appliedVersionCount).flatMap { (_, migrations) ->
            migrations.map { (name, id) ->
                mapOf("id" to id, "tagName" to name, "tagType" to MIGRATION_TAG_TYPE)
            }
        }
        val versions = definitions.take(appliedVersionCount).map { (version, _) ->
            mapOf("id" to version.third, "tagId" to version.second, "status" to STATUS_MIGRATE_COMPLETED)
        }
        var historyId = 700
        val migrations = definitions.take(appliedVersionCount).flatMap { (version, items) ->
            items.map { (_, itemTagId) ->
                mapOf("id" to historyId++, "tagId" to itemTagId, "versionId" to version.third)
            }
        }
        return OrchestrationHistory(tags, versions, migrations)
    }

    private fun withVersionStatus(
        history: OrchestrationHistory,
        versionId: Int,
        status: Int,
    ): OrchestrationHistory {
        val matchingVersions = history.versions.filter { it["id"] == versionId }
        assertEquals(1, matchingVersions.size, "Expected exactly one fixture Version row with id $versionId")
        val retryCheckpoint = checkpointForStatus(status)
        return history.copy(
            versions = history.versions.map { version ->
                if (version["id"] == versionId) {
                    version + mapOf("status" to status, "retryCheckpoint" to retryCheckpoint)
                } else {
                    version
                }
            },
        )
    }

    private fun checkpointForStatus(status: Int): String? {
        val isUp = status == MigrationVersionState.STATUS_MIGRATE_STARTED ||
            status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
        val isDown = status == MigrationVersionState.STATUS_ROLLBACK_STARTED ||
            status == MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
        if (!isUp && !isDown) return null

        return MigrationRetryCheckpointCodec.encode(
            MigrationRetryCheckpoint(
                formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                revision = 0,
                originalStatus = if (isUp) {
                    MigrationVersionState.STATUS_MIGRATE_STARTED
                } else {
                    MigrationVersionState.STATUS_ROLLBACK_STARTED
                },
                originalMessage = if (status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD ||
                    status == MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
                ) "original diagnostic" else null,
                cursorMigrationId = "create_tables",
                phase = if (isUp || isDown) {
                    if (status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD ||
                        status == MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
                    ) MigrationRetryPhase.RECORD_PENDING else MigrationRetryPhase.EXECUTION_PENDING
                } else {
                    error("Unexpected retry status $status")
                },
            ),
        )
    }

    private fun assertIncompleteVersionBlocks(
        history: OrchestrationHistory,
        expectedStatus: Int,
        command: (OperationExecutionScope) -> ExecuteResult<Boolean>,
    ) {
        VersionMigrationTrace.reset()
        database.respond(history.tags, history.versions, history.migrations)

        val result = OperationExecutionExecutor(versionTestContext()).connection(command)

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        val failureMessage = failure.message.orEmpty()
        if (expectedStatus == MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED) {
            assertTrue(failureMessage.contains("manual UP recovery"), failureMessage)
            assertTrue(failureMessage.contains("automatic Migration execution is blocked"), failureMessage)
        } else if (expectedStatus == MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED) {
            assertTrue(failureMessage.contains("manual DOWN recovery"), failureMessage)
            assertTrue(failureMessage.contains("automatic Migration execution is blocked"), failureMessage)
        } else {
            assertTrue(failureMessage.contains("non-completed status $expectedStatus"), failureMessage)
        }
        assertTrue(VersionMigrationTrace.events.isEmpty(), "No Migration UP/DOWN method may execute")

        // DML covers Version markers, Version/ITEM Tags, and Migration History writes/deletes.
        assertEquals(0, database.dmlCount)
        assertTrue(database.dmlCalls.none { it.sql.contains("system_migration_version", ignoreCase = true) })
        assertEquals(0, database.insertHistoryCount)
        assertEquals(0, database.deleteHistoryCount)
        assertEquals(0, database.deleteTagCount)
        assertTrue(database.events.none { it.startsWith("ddl:") }, "No Schema operation may execute")
        assertTrue(database.executedSql.none { it.contains("CREATE TABLE", ignoreCase = true) })

        // GET_LOCK is expected; the full History read must follow it and complete before release.
        val readIndexes = database.events.indices.filter { database.events[it].startsWith("read:") }
        assertEquals(3, readIndexes.size, "The complete Tag/Version/History snapshot must be read")
        val lockAcquire = database.events.indexOf("lock-acquire")
        val lockRelease = database.events.indexOf("lock-release")
        val connectionClose = database.events.indexOf("connection-close")
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
        assertTrue(lockAcquire >= 0 && lockAcquire < readIndexes.first())
        assertTrue(readIndexes.last() < lockRelease)
        assertTrue(lockRelease < connectionClose)
        assertEquals(1, database.connectionCloseCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    private fun executeSampleMigration(): ExecuteResult<Boolean> =
        OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(sampleConfiguration()).migrate(scope)
        }

    private fun sampleConfiguration(): MigrationConfiguration = migrationConfig {
        migrationVersion(VersionTagOne) { migration<SampleMigration>() }
    }

    private fun singleAppliedSampleHistory(): OrchestrationHistory {
        val tags = listOf(
            tag(VERSION_TAG_ID_ONE, VersionTagOne.value, VERSION_TAG_TYPE),
            tag(MIGRATION_TAG_ID, MigrationItem.value, MIGRATION_TAG_TYPE),
        )
        val versions = listOf(
            mapOf("id" to VERSION_ID_ONE, "tagId" to VERSION_TAG_ID_ONE, "status" to STATUS_MIGRATE_COMPLETED),
        )
        val migrations = listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE))
        return OrchestrationHistory(tags, versions, migrations)
    }

    private fun failureStatusCalls(): List<FakeDatabase.DmlCall> =
        database.dmlCalls.filter(::isFailureStatusWrite)

    private fun isItemTagInsert(call: FakeDatabase.DmlCall): Boolean =
        call.sql.contains("INSERT", ignoreCase = true) &&
            call.sql.contains("system_migration_tag", ignoreCase = true) &&
            call.parameters.firstOrNull() == MigrationItem.value

    private fun isHistoryInsert(call: FakeDatabase.DmlCall): Boolean =
        call.sql.contains("INSERT", ignoreCase = true) &&
            call.sql.contains("system_migration", ignoreCase = true) &&
            !call.sql.contains("system_migration_tag", ignoreCase = true) &&
            !call.sql.contains("system_migration_version", ignoreCase = true)

    private fun isHistoryDelete(call: FakeDatabase.DmlCall): Boolean =
        call.sql.contains("DELETE", ignoreCase = true) &&
            call.sql.contains("system_migration", ignoreCase = true) &&
            !call.sql.contains("system_migration_tag", ignoreCase = true) &&
            !call.sql.contains("system_migration_version", ignoreCase = true)

    private fun isFailureStatusWrite(call: FakeDatabase.DmlCall): Boolean =
        isVersionStatusUpdate(call) && call.sql.contains("SET status = ?, message = ?", ignoreCase = true)

    private fun isRecordingNormalizationCas(call: FakeDatabase.DmlCall): Boolean =
        isVersionStatusUpdate(call) &&
            call.sql.contains("SET status = ?", ignoreCase = true) &&
            !call.sql.contains("retry_checkpoint = NULL", ignoreCase = true) &&
            call.parameters.getOrNull(0) in setOf(
                MigrationVersionState.STATUS_MIGRATE_STARTED,
                MigrationVersionState.STATUS_ROLLBACK_STARTED,
            ) &&
            call.parameters.getOrNull(2) in setOf(
                MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
            )

    private fun isExecutionNormalizationCas(call: FakeDatabase.DmlCall): Boolean =
        isVersionStatusUpdate(call) &&
            call.sql.contains("SET status = ?", ignoreCase = true) &&
            call.sql.contains("CAST(retry_checkpoint AS BINARY)", ignoreCase = true) &&
            call.parameters.getOrNull(0) in setOf(
                MigrationVersionState.STATUS_MIGRATE_STARTED,
                MigrationVersionState.STATUS_ROLLBACK_STARTED,
            ) &&
            call.parameters.getOrNull(2) in setOf(
                MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN,
            )

    private fun isCheckpointUpdateTo(
        call: FakeDatabase.DmlCall,
        phase: MigrationRetryPhase,
        cursor: String?,
    ): Boolean {
        if (!call.sql.contains("SET retry_checkpoint = ?", ignoreCase = true)) return false
        val encoded = call.parameters.firstOrNull() as? String ?: return false
        return runCatching { MigrationRetryCheckpointCodec.decode(encoded) }
            .getOrNull()
            ?.let { it.phase == phase && it.cursorMigrationId == cursor }
            ?: false
    }

    private fun isVersionStatusUpdate(call: FakeDatabase.DmlCall): Boolean =
        call.sql.contains("UPDATE", ignoreCase = true) &&
            call.sql.contains("system_migration_version", ignoreCase = true)

    private fun isVersionDelete(call: FakeDatabase.DmlCall): Boolean =
        call.sql.contains("DELETE", ignoreCase = true) &&
            call.sql.contains("system_migration_version", ignoreCase = true)

    private fun assertStatusTransition(
        call: FakeDatabase.DmlCall,
        target: Int,
        versionId: Int,
        expected: Int,
        expectedStage: String,
        messageContains: String? = null,
        migrationId: String? = null,
        checkpointPhase: MigrationRetryPhase? = null,
        expectedOriginalMessage: String? = null,
    ) {
        assertTrue(isFailureStatusWrite(call), "Expected a conditional failure-status UPDATE: ${call.sql}")
        assertEquals(target, call.parameters[0])
        val diagnostic = call.parameters[1] as? String
            ?: error("Expected a persisted diagnostic message")
        val json = Json.parseToJsonElement(diagnostic).jsonObject
        assertEquals(1, json.getValue("schemaVersion").jsonPrimitive.int, diagnostic)
        assertEquals(expectedStage, json.getValue("stage").jsonPrimitive.content, diagnostic)
        assertEquals(versionId, json.getValue("versionId").jsonPrimitive.int, diagnostic)
        assertEquals(
            migrationId,
            json.getValue("migrationId").jsonPrimitive.contentOrNull,
            diagnostic,
        )
        assertTrue(json.getValue("exception").jsonPrimitive.content.isNotEmpty(), diagnostic)
        if (messageContains != null) {
            assertTrue(json.getValue("message").jsonPrimitive.content.contains(messageContains), diagnostic)
        }
        if (call.parameters.size == 6) {
            assertEquals(versionId, call.parameters[3])
            assertEquals(expected, call.parameters[4])
            assertTrue(call.sql.contains("SET status = ?, message = ?, retry_checkpoint = ?"), call.sql)
            val checkpoint = MigrationRetryCheckpointCodec.decode(call.parameters[2] as String)
            assertEquals(MigrationRetryCheckpointCodec.FORMAT_VERSION, checkpoint.formatVersion)
            assertNotNull(checkpoint.originalMessage)
            assertEquals(
                expectedOriginalMessage ?: diagnostic,
                checkpoint.originalMessage,
                "The first Diagnostic must be retained in the Checkpoint",
            )
            checkpointPhase?.let { assertEquals(it, checkpoint.phase) }
        } else {
            assertEquals(versionId, call.parameters[2])
            assertEquals(expected, call.parameters[3])
            assertTrue(call.sql.contains("SET status = ?, message = ?"), call.sql)
        }
    }

    private fun singleMigrationHistory(): OrchestrationHistory = OrchestrationHistory(
        tags = listOf(
            mapOf("id" to VERSION_TAG_ID_ONE, "tagName" to VersionTagOne.value, "tagType" to VERSION_TAG_TYPE),
            mapOf("id" to ITEM_TAG_ID_FIRST, "tagName" to VersionItemFirst.value, "tagType" to MIGRATION_TAG_TYPE),
        ),
        versions = listOf(
            mapOf("id" to VERSION_ID_ONE, "tagId" to VERSION_TAG_ID_ONE, "status" to STATUS_MIGRATE_COMPLETED),
        ),
        migrations = listOf(mapOf("id" to 700, "tagId" to ITEM_TAG_ID_FIRST, "versionId" to VERSION_ID_ONE)),
    )

    /** Queues the initial preflight and each selected Version's reads in execution order. */
    private fun respondForRollback(history: OrchestrationHistory, descendingVersionNames: List<String>) {
        fun respondSnapshot() = database.respond(history.tags, history.versions, history.migrations)
        respondSnapshot() // Preflight validates complete history before the first rollback.
        val migrationIdsByVersion = mapOf(
            VersionTagOne.value to listOf(VersionItemFirst.value to ITEM_TAG_ID_FIRST, VersionItemSecond.value to ITEM_TAG_ID_SECOND),
            VersionTagTwo.value to listOf(VersionItemThird.value to ITEM_TAG_ID_THIRD),
            VersionTagThree.value to listOf(VersionItemFourth.value to ITEM_TAG_ID_FOURTH, VersionItemFifth.value to ITEM_TAG_ID_FIFTH),
            VersionTagFour.value to emptyList(),
        )
        descendingVersionNames.forEach { versionName ->
            respondSnapshot() // Per-Version history validation.
            val versionId = history.versions.first { it["tagId"] == when (versionName) {
                VersionTagOne.value -> VERSION_TAG_ID_ONE
                VersionTagTwo.value -> VERSION_TAG_ID_TWO
                VersionTagThree.value -> VERSION_TAG_ID_THREE
                else -> VERSION_TAG_ID_FOUR
            } }["id"]
            migrationIdsByVersion.getValue(versionName).asReversed().forEach { (name, tagId) ->
                val tagName = history.tags.first { it["id"] == tagId }["tagName"]
                database.respond(
                    listOf(mapOf("id" to tagId, "tagType" to MIGRATION_TAG_TYPE)),
                    history.migrations.filter { it["tagId"] == tagId && it["versionId"] == versionId }
                        .map { mapOf("id" to it["id"], "versionId" to it["versionId"]) },
                    emptyList(), // No history remains after deleting this item.
                )
                assertEquals(name, tagName)
            }
        }
    }

    private data class OrchestrationHistory(
        val tags: List<Map<String, Any?>>,
        val versions: List<Map<String, Any?>>,
        val migrations: List<Map<String, Any?>>,
    )

    @Test
    fun `version service failure remains latched when caller ignores its returned result`() {
        val context = versionTestContext()
        database.failDdlCalls += 1
        database.respond(emptyList(), emptyList())
        val service = MigrationVersionTransactionService(versionConfiguration(firstRegistration))

        val outerResult = OperationExecutionExecutor(context).connection { scope ->
            assertIs<ExecuteResult.Failure>(service.transactionUp(scope, VersionTag))
            val statementCountAfterFailure = database.executedSql.size
            assertIs<ExecuteResult.Failure>(service.transactionUp(scope, VersionTag))
            assertEquals(statementCountAfterFailure, database.executedSql.size)
            ExecuteResult.Success(true)
        }

        assertIs<ExecuteResult.Failure>(outerResult)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionCloseCount)
    }

    @Test
    fun `state reads latest raw version under migration lock without writes`() {
        val message = """{"schemaVersion":1,"versionId":41,"stage":"UP_EXECUTION"}"""
        database.respond(
            listOf(
                mapOf("id" to 41, "tagId" to 12, "status" to 91, "message" to message),
                mapOf(
                    "id" to 18,
                    "tagId" to 7,
                    "status" to MigrationVersionState.STATUS_MIGRATE_COMPLETED,
                    "message" to null,
                ),
            ),
        )
        val controller = MigrationController(MigrationConfiguration(emptyList()))

        val operationResult = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            controller.state(activeScope)
        }
        val state = assertIs<ExecuteResult.Success<MigrationVersionState?>>(
            operationResult,
            (operationResult as? ExecuteResult.Failure)?.exception?.stackTraceToString().orEmpty(),
        ).result

        assertEquals(MigrationVersionState(41, 12, 91, message), state)
        assertTrue(database.events.indexOf("lock-acquire") >= 0)
        assertTrue(database.events.indexOf("lock-acquire") < database.events.indexOfFirst { it.startsWith("read:") })
        assertTrue(database.events.indexOf("lock-release") > database.events.indexOfFirst { it.startsWith("read:") })
        assertEquals(0, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("ddl:") })
        val versionQuery = database.executedSql.single {
            "system_migration_version" in it.lowercase() && "message" in it.lowercase()
        }.lowercase()
        assertTrue(versionQuery.contains("order by"))
        assertTrue(versionQuery.contains("desc"))
        assertTrue(versionQuery.contains("limit 1"))
        assertTrue("insert" !in versionQuery && "update" !in versionQuery && "delete" !in versionQuery)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(1, database.connectionCloseCount)
    }

    @Test
    fun `state returns null for an empty version table through the managed operation`() {
        database.respond(emptyList())
        val controller = MigrationController(MigrationConfiguration(emptyList()))

        val operationResult = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            controller.state(activeScope)
        }
        assertNull(assertIs<ExecuteResult.Success<MigrationVersionState?>>(operationResult).result)
        assertEquals(0, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `state can select the latest incomplete version under the migration lock`() {
        val message = """{"schemaVersion":1,"versionId":88,"stage":"UP_EXECUTION"}"""
        database.respond(listOf(mapOf("id" to 88, "tagId" to 8, "status" to 9876, "message" to message)))
        val controller = MigrationController(MigrationConfiguration(emptyList()))

        val operationResult = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            controller.state(activeScope, lastIncompleteVersion = true)
        }
        val state = assertIs<ExecuteResult.Success<MigrationVersionState?>>(operationResult).result

        assertEquals(MigrationVersionState(88, 8, 9876, message), state)
        val versionQuery = database.executedSql.single {
            "system_migration_version" in it.lowercase() && "message" in it.lowercase()
        }.lowercase()
        assertTrue(Regex("status\\s*(?:<>|!=)\\s*\\?").containsMatchIn(versionQuery), versionQuery)
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
        assertTrue(database.events.indexOf("lock-acquire") < database.events.indexOfFirst { it.startsWith("read:") })
        assertTrue(database.events.indexOf("lock-release") > database.events.indexOfFirst { it.startsWith("read:") })
        assertEquals(0, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `state query failure is propagated by the enclosing operation and releases the lock`() {
        database.failReadCalls += 1
        val controller = MigrationController(MigrationConfiguration(emptyList()))

        val operationResult = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            controller.state(activeScope)
        }

        val failure = assertIs<SQLException>(assertIs<ExecuteResult.Failure>(operationResult).exception)
        assertEquals("Read 1 failed", failure.message)
        assertTrue(database.events.indexOf("lock-acquire") < database.events.indexOf("lock-release"))
        assertEquals(0, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `typed latest incomplete state resolves exact diagnostic migration under the migration lock`() {
        val diagnostic = """{"schemaVersion":1,"versionId":${VERSION_ID_ONE},"migrationId":"${firstRegistration.id.value}"}"""
        val (result, db) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration),
            selectedRows = listOf(
                versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                    diagnostic,
                ),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                ),
            ),
            selection = MigrationVersionSelection.LATEST_INCOMPLETE,
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertEquals(
            MigrationVersionState(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                diagnostic,
            ),
            target.version,
        )
        assertEquals(firstRegistration, target.migration)
        assertEquals(MigrationRecoveryOperation.UP_EXECUTION, target.operation)
        assertEquals(1, db.events.count { it == "lock-acquire" })
        assertEquals(1, db.events.count { it == "lock-release" })
        assertTrue(db.events.indexOf("lock-acquire") < db.events.indexOfFirst { it.startsWith("read:") })
        assertTrue(db.events.indexOf("lock-release") > db.events.indexOfFirst { it.startsWith("read:") })
        assertEquals(0, db.dmlCount)
        assertTrue(db.events.none { it.startsWith("ddl:") })
        assertTrue(VersionMigrationTrace.events.isEmpty())
    }

    @Test
    fun `typed state maps known and unknown persisted statuses without rewriting the raw status`() {
        val expected = mapOf(
            MigrationVersionState.STATUS_MIGRATE_STARTED to MigrationRecoveryOperation.UNKNOWN,
            MigrationVersionState.STATUS_MIGRATE_FAILED_UP to MigrationRecoveryOperation.UP_EXECUTION,
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD to MigrationRecoveryOperation.UNKNOWN,
            MigrationVersionState.STATUS_MIGRATE_COMPLETED to MigrationRecoveryOperation.MIGRATE_DONE,
            MigrationVersionState.STATUS_ROLLBACK_STARTED to MigrationRecoveryOperation.UNKNOWN,
            MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN to MigrationRecoveryOperation.DOWN_EXECUTION,
            MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD to MigrationRecoveryOperation.UNKNOWN,
            MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED to MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED,
            MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED to MigrationRecoveryOperation.DOWN_MANUAL_RECOVERY_REQUIRED,
            943 to MigrationRecoveryOperation.UNKNOWN,
        )

        expected.forEach { (status, operation) ->
            val (result, db) = readRecoveryTarget(
                configuration = MigrationConfiguration(emptyList()),
                selectedRows = listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, status, null)),
                snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, "unregistered_version", VERSION_TAG_TYPE)),
                snapshotVersions = listOf(versionSnapshotRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, status)),
            )

            val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
            assertEquals(status, target.version.status)
            assertEquals(operation, target.operation)
            assertNull(target.migration)
            assertEquals(0, db.dmlCount)
            assertTrue(db.events.none { it.startsWith("ddl:") })
        }
    }

    @Test
    fun `typed state classifies started Versions through the shared checkpoint contract`() {
        val cases = listOf(
            MigrationRetryPhase.EXECUTION_PENDING to MigrationRecoveryOperation.START_EXECUTION,
            MigrationRetryPhase.EXECUTION_IN_FLIGHT to MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED,
            MigrationRetryPhase.RECORD_PENDING to MigrationRecoveryOperation.UP_RECORDING_EXECUTION,
            MigrationRetryPhase.PAUSED_NEXT to MigrationRecoveryOperation.START_EXECUTION,
            MigrationRetryPhase.FINALIZATION_PENDING to MigrationRecoveryOperation.UP_RECORDING_EXECUTION,
        )

        cases.forEach { (phase, expectedOperation) ->
            val checkpoint = MigrationRetryCheckpointCodec.encode(
                MigrationRetryCheckpoint(
                    formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                    revision = 1,
                    originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
                    originalMessage = "original diagnostic",
                    cursorMigrationId = if (phase == MigrationRetryPhase.FINALIZATION_PENDING) null else "create_tables",
                    phase = phase,
                ),
            )
            val (result, _) = readRecoveryTarget(
                configuration = MigrationConfiguration(emptyList()),
                selectedRows = listOf(
                    versionStateRow(
                        VERSION_ID_ONE,
                        VERSION_TAG_ID_ONE,
                        MigrationVersionState.STATUS_MIGRATE_STARTED,
                        null,
                        checkpoint,
                    ),
                ),
                snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, "unregistered_version", VERSION_TAG_TYPE)),
                snapshotVersions = listOf(
                    versionSnapshotRow(
                        VERSION_ID_ONE,
                        VERSION_TAG_ID_ONE,
                        MigrationVersionState.STATUS_MIGRATE_STARTED,
                        checkpoint,
                    ),
                ),
            )

            val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
            assertEquals(expectedOperation, target.operation, "phase=$phase")
        }

        val (malformedResult, _) = readRecoveryTarget(
            configuration = MigrationConfiguration(emptyList()),
            selectedRows = listOf(
                versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, "bad-json"),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, "unregistered_version", VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, "bad-json"),
            ),
        )
        assertEquals(
            MigrationRecoveryOperation.UNKNOWN,
            assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(malformedResult).result).operation,
        )
    }

    @Test
    fun `typed state does not fabricate a migration for a version-level null identity`() {
        val diagnostic = """{"schemaVersion":1,"versionId":${VERSION_ID_ONE},"migrationId":null}"""
        val checkpoint = MigrationRetryCheckpointCodec.encode(
            MigrationRetryCheckpoint(
                formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                revision = 1,
                originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
                originalMessage = diagnostic,
                cursorMigrationId = null,
                phase = MigrationRetryPhase.FINALIZATION_PENDING,
            ),
        )
        val (result, _) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration),
            selectedRows = listOf(
                versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                    diagnostic,
                    checkpoint,
                ),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                    checkpoint,
                ),
            ),
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertNull(target.migration)
        assertEquals(MigrationRecoveryOperation.UP_RECORDING_EXECUTION, target.operation)
    }

    @Test
    fun `typed state resolves the current checkpoint cursor instead of the original diagnostic migration`() {
        val diagnostic = """{"schemaVersion":1,"versionId":$VERSION_ID_ONE,"migrationId":"${firstRegistration.id.value}"}"""
        val checkpoint = MigrationRetryCheckpointCodec.encode(
            MigrationRetryCheckpoint(
                formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                revision = 3,
                originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
                originalMessage = diagnostic,
                cursorMigrationId = secondRegistration.id.value,
                phase = MigrationRetryPhase.PAUSED_NEXT,
            ),
        )
        val (result, _) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration, secondRegistration),
            selectedRows = listOf(
                versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    diagnostic,
                    checkpoint,
                ),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    checkpoint,
                ),
            ),
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertEquals(secondRegistration, target.migration)
        assertEquals(MigrationRecoveryOperation.START_EXECUTION, target.operation)
        assertEquals(3L, target.checkpointRevision)
    }

    @Test
    fun `retry target validator accepts a current locked snapshot`() {
        val checkpoint = MigrationRetryCheckpointCodec.encode(
            MigrationRetryCheckpoint(
                formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                revision = 5,
                originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
                originalMessage = null,
                cursorMigrationId = firstRegistration.id.value,
                phase = MigrationRetryPhase.EXECUTION_PENDING,
            ),
        )
        val target = MigrationRecoveryTarget(
            MigrationVersionState(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_MIGRATE_STARTED,
                null,
            ),
            firstRegistration,
            MigrationRecoveryOperation.START_EXECUTION,
            5L,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpoint)),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            activeScope.withSystemLock(SystemLock.MIGRATION, 60) { lockedScope ->
                MigrationVersionStateService(versionConfiguration(firstRegistration))
                    .validateTargetWhileMigrationLockHeld(lockedScope, target)
            }
        }

        val validated = assertIs<ExecuteResult.Success<MigrationRecoveryTarget>>(result).result
        assertEquals(5L, validated?.checkpointRevision)
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertTrue(database.events.indexOf("lock-acquire") < database.events.indexOfFirst { it.startsWith("read:") })
        assertTrue(database.events.indexOf("lock-release") > database.events.indexOfFirst { it.startsWith("read:") })
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `retry target validator rejects a stale locked checkpoint revision before any write`() {
        val checkpoint = MigrationRetryCheckpointCodec.encode(
            MigrationRetryCheckpoint(
                formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                revision = 6,
                originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
                originalMessage = null,
                cursorMigrationId = firstRegistration.id.value,
                phase = MigrationRetryPhase.EXECUTION_PENDING,
            ),
        )
        val staleTarget = MigrationRecoveryTarget(
            MigrationVersionState(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_MIGRATE_STARTED,
                null,
            ),
            firstRegistration,
            MigrationRecoveryOperation.START_EXECUTION,
            5L,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpoint)),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            activeScope.withSystemLock(SystemLock.MIGRATION, 60) { lockedScope ->
                MigrationVersionStateService(versionConfiguration(firstRegistration))
                    .validateTargetWhileMigrationLockHeld(lockedScope, staleTarget)
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertTrue(database.events.indexOf("lock-release") > database.events.indexOfFirst { it.startsWith("read:") })
        assertEquals(0, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("ddl:") })
    }

    @Test
    fun `typed state does not guess a migration when a valid checkpoint has no cursor`() {
        val checkpoint = MigrationRetryCheckpointCodec.encode(
            MigrationRetryCheckpoint(
                formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                revision = 1,
                originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
                originalMessage = null,
                cursorMigrationId = null,
                phase = MigrationRetryPhase.FINALIZATION_PENDING,
            ),
        )
        val (result, _) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration),
            selectedRows = listOf(
                versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    null,
                    checkpoint,
                ),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    checkpoint,
                ),
            ),
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertNull(target.migration)
        assertEquals(MigrationRecoveryOperation.UP_RECORDING_EXECUTION, target.operation)
    }

    @Test
    fun `typed state does not use diagnostic fallback when a persisted checkpoint is malformed`() {
        val diagnostic = """{"schemaVersion":1,"versionId":$VERSION_ID_ONE,"migrationId":"${firstRegistration.id.value}"}"""
        val (result, _) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration),
            selectedRows = listOf(
                versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    diagnostic,
                    "malformed-checkpoint",
                ),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    "malformed-checkpoint",
                ),
            ),
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertEquals(MigrationRecoveryOperation.UNKNOWN, target.operation)
        assertNull(target.migration)
    }

    @Test
    fun `typed state can select one exact version id`() {
        val (result, db) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration),
            selectedRows = listOf(
                versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                    null,
                ),
                versionStateRow(
                    VERSION_ID_TWO,
                    VERSION_TAG_ID_TWO,
                    MigrationVersionState.STATUS_MIGRATE_COMPLETED,
                    null,
                ),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                ),
            ),
            versionId = VERSION_ID_ONE,
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertEquals(VERSION_ID_ONE, target.version.versionId)
        assertEquals(firstRegistration, target.migration)
        val query = db.executedSql.first { "system_migration_version" in it.lowercase() }.lowercase()
        assertTrue(Regex("smv\\.id\\s*=\\s*\\?").containsMatchIn(query), query)
        assertEquals(listOf(VERSION_ID_ONE), db.readParameters.first())
    }

    @Test
    fun `typed state returns null for an absent explicit version id`() {
        val (result, db) = readRecoveryTarget(
            configuration = MigrationConfiguration(emptyList()),
            selectedRows = emptyList(),
            snapshotTags = emptyList(),
            snapshotVersions = emptyList(),
            versionId = 9876,
        )

        assertNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertEquals(listOf(9876), db.readParameters.first())
        assertEquals(1, db.events.count { it == "lock-acquire" })
        assertEquals(1, db.events.count { it == "lock-release" })
        assertEquals(0, db.dmlCount)
    }

    @Test
    fun `typed state does not fall back when diagnostic identity disagrees with the selected version registry`() {
        val diagnostic = """{"schemaVersion":1,"versionId":${VERSION_ID_ONE},"migrationId":"${secondRegistration.id.value}"}"""
        val (result, _) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration),
            selectedRows = listOf(
                versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                    diagnostic,
                ),
            ),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(
                versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                ),
            ),
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertNull(target.migration)
    }

    @Test
    fun `typed state leaves ambiguous configured migrations unresolved`() {
        val (result, _) = readRecoveryTarget(
            configuration = versionConfiguration(firstRegistration, secondRegistration),
            selectedRows = listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, 77, "legacy message")),
            snapshotTags = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            snapshotVersions = listOf(versionSnapshotRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, 77)),
        )

        val target = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertEquals(MigrationRecoveryOperation.UNKNOWN, target.operation)
        assertNull(target.migration)
    }

    @Test
    fun `typed state returns null when no selected version exists`() {
        val (result, db) = readRecoveryTarget(
            configuration = MigrationConfiguration(emptyList()),
            selectedRows = emptyList(),
            snapshotTags = emptyList(),
            snapshotVersions = emptyList(),
        )

        assertNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(result).result)
        assertEquals(1, db.events.count { it == "lock-acquire" })
        assertEquals(1, db.events.count { it == "lock-release" })
        assertEquals(0, db.dmlCount)
    }

    @Test
    fun `typed latest selection can choose completed latest while incomplete selection skips it`() {
        val rows = listOf(
            versionStateRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                null,
            ),
            versionStateRow(VERSION_ID_TWO, VERSION_TAG_ID_TWO, STATUS_MIGRATE_COMPLETED, null),
        )
        val tags = listOf(
            tag(VERSION_TAG_ID_ONE, "version_one", VERSION_TAG_TYPE),
            tag(VERSION_TAG_ID_TWO, "version_two", VERSION_TAG_TYPE),
        )
        val versions = listOf(
            versionSnapshotRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
            ),
            versionSnapshotRow(VERSION_ID_TWO, VERSION_TAG_ID_TWO, STATUS_MIGRATE_COMPLETED),
        )

        val (latestResult, latestDb) = readRecoveryTarget(
            MigrationConfiguration(emptyList()), rows, tags, versions, MigrationVersionSelection.LATEST,
        )
        val latest = assertNotNull(assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(latestResult).result)
        assertEquals(VERSION_ID_TWO, latest.version.versionId)
        val latestSql = latestDb.executedSql.single { "system_migration_version" in it.lowercase() }
        assertTrue(!Regex("status\\s*(?:<>|!=)").containsMatchIn(latestSql), latestSql)

        val (incompleteResult, incompleteDb) = readRecoveryTarget(
            MigrationConfiguration(emptyList()), rows, tags, versions, MigrationVersionSelection.LATEST_INCOMPLETE,
        )
        val incomplete = assertNotNull(
            assertIs<ExecuteResult.Success<MigrationRecoveryTarget?>>(incompleteResult).result,
        )
        assertEquals(VERSION_ID_ONE, incomplete.version.versionId)
        val incompleteSql = incompleteDb.executedSql.single { "system_migration_version" in it.lowercase() }
        assertTrue(Regex("status\\s*(?:<>|!=)\\s*\\?").containsMatchIn(incompleteSql), incompleteSql)
        assertEquals(listOf(STATUS_MIGRATE_COMPLETED), incompleteDb.readParameters.first())
    }

    @Test
    fun `recovery dispatches UP start through retryUp and preserves pause result`() {
        val configuration = retryVersionConfiguration(first = true, second = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        val row = versionStateRow(
            VERSION_ID_ONE,
            VERSION_TAG_ID_ONE,
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            null,
            checkpointJson(checkpoint),
        )
        val versionTag = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE))
        database.respond(listOf(row), versionTag, listOf(row), versionTag)

        val result = callRecovery(configuration, toCompleteVersion = false)

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first"), VersionMigrationTrace.events)
        assertEquals(1, database.insertHistoryCount)
        assertEquals(
            MigrationRetryPhase.PAUSED_NEXT,
            MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE])).phase,
        )
    }

    @Test
    fun `recovery true continues through the end of the same UP version`() {
        val configuration = retryVersionConfiguration(first = true, second = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        val row = versionStateRow(
            VERSION_ID_ONE,
            VERSION_TAG_ID_ONE,
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            null,
            checkpointJson(checkpoint),
        )
        val versionTag = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE))
        database.respond(listOf(row), versionTag, listOf(row), versionTag)

        val result = callRecovery(configuration, toCompleteVersion = true)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first", "up:second"), VersionMigrationTrace.events)
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
    }

    @Test
    fun `recovery dispatches DOWN start through retryDown`() {
        val configuration = retryVersionConfiguration(first = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        val row = versionStateRow(
            VERSION_ID_ONE,
            VERSION_TAG_ID_ONE,
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            null,
            checkpointJson(checkpoint),
        )
        val versionTag = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE))
        database.respond(
            listOf(row),
            versionTag,
            listOf(row),
            versionTag,
            listOf(tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE)),
            listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE)),
            emptyList(),
        )

        val result = callRecovery(configuration, toCompleteVersion = false)

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:first"), VersionMigrationTrace.events)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
    }

    @Test
    fun `recovery dispatches UP and DOWN recording states without rerunning schema`() {
        for (up in listOf(true, false)) {
            database = FakeDatabase()
            scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
            VersionMigrationTrace.reset()

            val startedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_STARTED
            else MigrationVersionState.STATUS_ROLLBACK_STARTED
            val failedRecordingStatus = if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
            else MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
            val diagnostic = retryRecordingDiagnostic(up, migrationId = null)
            val checkpoint = retryCheckpoint(
                startedStatus,
                4,
                cursor = null,
                phase = MigrationRetryPhase.FINALIZATION_PENDING,
                originalMessage = diagnostic,
            )
            val row = versionStateRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                failedRecordingStatus,
                diagnostic,
                checkpointJson(checkpoint),
            )
            val versionTag = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE))
            database.respond(listOf(row), versionTag, listOf(row), versionTag)

            val result = callRecovery(retryVersionConfiguration(first = true), toCompleteVersion = true)

            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
            assertTrue(VersionMigrationTrace.events.isEmpty())
            if (up) {
                assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
            } else {
                assertTrue(VERSION_ID_ONE !in database.versionStatuses)
            }
        }
    }

    @Test
    fun `recovery dispatches UP and DOWN execution failures through validated Retry`() {
        for (up in listOf(true, false)) {
            database = FakeDatabase()
            scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
            VersionMigrationTrace.reset()

            val startedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_STARTED
            else MigrationVersionState.STATUS_ROLLBACK_STARTED
            val failedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_UP
            else MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN
            val diagnostic = MigrationFailureDiagnostic(
                stage = if (up) MigrationFailureDiagnosticStage.UP_EXECUTION
                else MigrationFailureDiagnosticStage.DOWN_EXECUTION,
                exceptionClassName = "java.sql.SQLException",
                sqlState = "42S21",
                vendorErrorCode = 1060,
                message = "Duplicate column name",
                migrationId = VersionItemFirst.value,
            ).toStorageMessage(VERSION_ID_ONE)
            val checkpoint = retryCheckpoint(
                startedStatus,
                4,
                VersionItemFirst.value,
                MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                originalMessage = diagnostic,
            )
            val versionRow = versionStateRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                failedStatus,
                diagnostic,
                checkpointJson(checkpoint),
            )
            val versionTag = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE))
            val responses = mutableListOf<List<Map<String, Any?>>>(
                listOf(versionRow),
                versionTag,
                listOf(versionRow),
                versionTag,
            )
            if (!up) {
                // DOWN preflight and Recording must see the existing Version-owned History.
                responses += listOf(tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE))
                responses += listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE))
                responses.add(emptyList())
            }
            database.respond(*responses.toTypedArray())

            val result = callRecovery(retryVersionConfiguration(first = true), toCompleteVersion = true)

            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result, "UP=$up").result)
            assertEquals(listOf(if (up) "up:first" else "down:first"), VersionMigrationTrace.events)
            assertEquals(1, database.events.count { it.startsWith("ddl:") }, "UP=$up")
            assertEquals(failedStatus, database.dmlCalls.first().parameters[2], "normalization must match prior status")
            if (up) {
                assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
                assertNull(database.versionCheckpoints[VERSION_ID_ONE])
            } else {
                assertTrue(VERSION_ID_ONE !in database.versionStatuses)
            }
            assertEquals(2, database.events.count { it == "lock-acquire" }, "state read and Retry each own a lock")
            assertEquals(2, database.events.count { it == "lock-release" }, "UP=$up")
        }
    }

    @Test
    fun `recovery resumes UP and DOWN recording failures without rerunning schema`() {
        for (up in listOf(true, false)) {
            database = FakeDatabase()
            scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
            VersionMigrationTrace.reset()

            val startedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_STARTED
            else MigrationVersionState.STATUS_ROLLBACK_STARTED
            val failedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
            else MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
            val diagnostic = retryRecordingDiagnostic(up, migrationId = VersionItemFirst.value)
            val checkpoint = retryCheckpoint(
                startedStatus,
                4,
                VersionItemFirst.value,
                MigrationRetryPhase.RECORD_PENDING,
                originalMessage = diagnostic,
            )
            val versionRow = versionStateRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                failedStatus,
                diagnostic,
                checkpointJson(checkpoint),
            )
            val versionTag = tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)
            val responses = mutableListOf<List<Map<String, Any?>>>(
                listOf(versionRow),
                listOf(versionTag),
                listOf(versionRow),
                listOf(versionTag),
            )
            if (up) {
                // Reuse the valid ITEM Tag and supply no History so only the missing History is inserted.
                responses += listOf(tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE))
                responses.add(emptyList())
            } else {
                // DOWN reconciliation sees the selected Tag, Version, and History before deleting metadata.
                responses += listOf(
                    versionTag,
                    tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE),
                )
                responses += listOf(versionSnapshotRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    MigrationVersionState.STATUS_ROLLBACK_STARTED,
                    checkpointJson(checkpoint),
                ))
                responses += listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE))
                responses.add(emptyList())
            }
            database.respond(*responses.toTypedArray())

            val result = callRecovery(retryVersionConfiguration(first = true), toCompleteVersion = true)

            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result, "UP=$up").result)
            assertTrue(VersionMigrationTrace.events.isEmpty(), "UP=$up: RECORD_PENDING must not execute Schema")
            assertTrue(database.events.none { it.startsWith("ddl:") }, "UP=$up: no Schema DDL")
            if (up) {
                assertEquals(1, database.insertHistoryCount, "UP Recording inserts only its missing History")
                assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
            } else {
                assertEquals(
                    1,
                    database.dmlCalls.count { call ->
                        call.sql.contains("DELETE", ignoreCase = true) &&
                            call.sql.contains("system_migration", ignoreCase = true) &&
                            !call.sql.contains("system_migration_tag", ignoreCase = true) &&
                            !call.sql.contains("system_migration_version", ignoreCase = true)
                    },
                    "DOWN Recording deletes only its target History",
                )
                assertEquals(1, database.deleteTagCount)
                assertTrue(VERSION_ID_ONE !in database.versionStatuses)
            }
        }
    }

    @Test
    fun `recovery rejects manual and unknown statuses without retrying`() {
        val configuration = retryVersionConfiguration(first = true)
        val rejectedStatuses = listOf(
            MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED,
            MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED,
            999,
        )

        for (status in rejectedStatuses) {
            database = FakeDatabase()
            scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
            VersionMigrationTrace.reset()
            database.respond(
                listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, status, null)),
                listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            )

            val result = callRecovery(configuration, toCompleteVersion = true)

            assertIs<ExecuteResult.Failure>(result, "status=$status")
            assertTrue(VersionMigrationTrace.events.isEmpty(), "status=$status")
            assertEquals(0, database.dmlCount, "status=$status")
            assertEquals(1, database.events.count { it == "lock-acquire" }, "status=$status")
        }
    }

    @Test
    fun `recovery returns Success null when no incomplete version exists`() {
        database.respond(emptyList())

        val result = callRecovery(retryVersionConfiguration(first = true), toCompleteVersion = true)

        val success = assertIs<ExecuteResult.Success<Boolean>>(result)
        assertNull(success.result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
    }

    @Test
    fun `recovery transfers state read failure unchanged`() {
        val failure = SQLException("state read failure")
        database.readFailure = failure
        database.failReadCalls += 1

        val result = callRecovery(retryVersionConfiguration(first = true), toCompleteVersion = true)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
    }

    @Test
    fun `recovery transfers Retry failure unchanged`() {
        val configuration = retryVersionConfiguration(first = true)
        val diagnostic = retryRecordingDiagnostic(up = true, migrationId = null)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            4,
            cursor = null,
            phase = MigrationRetryPhase.FINALIZATION_PENDING,
            originalMessage = diagnostic,
        )
        val row = versionStateRow(
            VERSION_ID_ONE,
            VERSION_TAG_ID_ONE,
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            diagnostic,
            checkpointJson(checkpoint),
        )
        val versionTag = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE))
        database.respond(listOf(row), versionTag, listOf(row), versionTag)
        val failure = SQLException("Retry normalization failed")
        database.failDmlWhen = { failure }

        val result = callRecovery(configuration, toCompleteVersion = true)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(2, database.events.count { it == "lock-acquire" })
        assertEquals(2, database.events.count { it == "lock-release" })
    }

    @Test
    fun `recovery rejects a checkpoint changed between state selection and Retry validation`() {
        val configuration = retryVersionConfiguration(first = true)
        val initial = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        val changed = initial.copy(revision = 1)
        val versionTag = listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE))
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(initial))),
            versionTag,
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(changed))),
            versionTag,
        )

        val result = callRecovery(configuration, toCompleteVersion = true)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(2, database.events.count { it == "lock-acquire" })
        assertEquals(2, database.events.count { it == "lock-release" })
    }

    @Test
    fun `defensive dispatch does not retry MIGRATE_DONE or incompatible START status`() {
        val configuration = retryVersionConfiguration(first = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        val orchestration = MigrationRunOrchestrationService(configuration)
        val completedTarget = retryTarget(
            MigrationVersionState.STATUS_MIGRATE_COMPLETED,
            checkpoint,
            firstRegistration,
            MigrationRecoveryOperation.MIGRATE_DONE,
        )

        val completed = orchestration.dispatchRecovery(completedTarget, true, scope)

        val completedSuccess = assertIs<ExecuteResult.Success<Boolean>>(completed)
        assertNull(completedSuccess.result)
        assertEquals(0, database.dmlCount)
        assertTrue(VersionMigrationTrace.events.isEmpty())

        val incompatibleStart = completedTarget.copy(
            version = completedTarget.version.copy(status = MigrationVersionState.STATUS_MIGRATE_FAILED_UP),
            operation = MigrationRecoveryOperation.START_EXECUTION,
        )
        val rejected = orchestration.dispatchRecovery(incompatibleStart, true, scope)

        assertIs<ExecuteResult.Failure>(rejected)
        assertEquals(0, database.dmlCount)
        assertTrue(VersionMigrationTrace.events.isEmpty())
    }

    @Test
    fun `retry UP execution pending executes and records under one migration lock`() {
        val configuration = retryVersionConfiguration(first = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        val target = retryTarget(MigrationVersionState.STATUS_MIGRATE_STARTED, checkpoint, firstRegistration)
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, target.version.status, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )

        val result = callRetryUp(configuration, target, toCompleteVersion = true)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first"), VersionMigrationTrace.events)
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
        assertNull(database.versionCheckpoints[VERSION_ID_ONE])
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
    }

    @Test
    fun `retry UP false from execution pending pauses before the second migration`() {
        val configuration = retryVersionConfiguration(first = true, second = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )

        val result = callRetryUp(
            configuration,
            retryTarget(MigrationVersionState.STATUS_MIGRATE_STARTED, checkpoint, firstRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first"), VersionMigrationTrace.events)
        assertEquals(1, database.insertHistoryCount)
        val paused = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.PAUSED_NEXT, paused.phase)
        assertEquals(VersionItemSecond.value, paused.cursorMigrationId)
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, database.versionStatuses[VERSION_ID_ONE])
    }

    @Test
    fun `retry UP false from paused next executes stored cursor and pauses before the following migration`() {
        val configuration = migrationConfig {
            migrationVersion(VersionTag) {
                migration<VersionFirstMigration>()
                migration<VersionSecondMigration>()
                migration<VersionThirdMigration>()
            }
        }
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            3,
            VersionItemSecond.value,
            MigrationRetryPhase.PAUSED_NEXT,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(checkpoint))),
            listOf(
                tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
                tag(ITEM_TAG_ID_FIRST, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
        )

        val result = callRetryUp(
            configuration,
            retryTarget(MigrationVersionState.STATUS_MIGRATE_STARTED, checkpoint, secondRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:second"), VersionMigrationTrace.events)
        assertEquals(1, database.insertHistoryCount)
        val paused = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.PAUSED_NEXT, paused.phase)
        assertEquals(VersionItemThird.value, paused.cursorMigrationId)
    }

    @Test
    fun `retry UP false persists finalization pending and a later false call finalizes only`() {
        val configuration = retryVersionConfiguration(first = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )

        val pausedResult = callRetryUp(
            configuration,
            retryTarget(MigrationVersionState.STATUS_MIGRATE_STARTED, checkpoint, firstRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(pausedResult).result)
        val finalization = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.FINALIZATION_PENDING, finalization.phase)
        assertNull(finalization.cursorMigrationId)
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, database.versionStatuses[VERSION_ID_ONE])
        assertEquals(listOf("up:first"), VersionMigrationTrace.events)

        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(finalization))),
            listOf(
                tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
                tag(GENERATED_TAG_ID.toInt(), VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
        )
        val finalizationTarget = retryTarget(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            finalization,
            migration = null,
        )
        val completedResult = callRetryUp(configuration, finalizationTarget, toCompleteVersion = false)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(completedResult).result)
        assertEquals(listOf("up:first"), VersionMigrationTrace.events)
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
        assertNull(database.versionCheckpoints[VERSION_ID_ONE])
        assertEquals(2, database.events.count { it == "lock-acquire" })
    }

    @Test
    fun `retry UP RECORD_PENDING records without running schema and pauses at the next cursor`() {
        val configuration = retryVersionConfiguration(first = true, second = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            2,
            VersionItemFirst.value,
            MigrationRetryPhase.RECORD_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )

        val result = callRetryUp(
            configuration,
            retryTarget(MigrationVersionState.STATUS_MIGRATE_STARTED, checkpoint, firstRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        val paused = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.PAUSED_NEXT, paused.phase)
        assertEquals(VersionItemSecond.value, paused.cursorMigrationId)
        assertEquals(1, database.insertHistoryCount)
        assertEquals(1, database.events.count { it.startsWith("insert-tag:") })
    }

    @Test
    fun `retry UP PAUSED_NEXT resumes exactly the stored cursor`() {
        val configuration = retryVersionConfiguration(first = true, second = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            3,
            VersionItemSecond.value,
            MigrationRetryPhase.PAUSED_NEXT,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(checkpoint))),
            listOf(
                tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
                tag(GENERATED_TAG_ID.toInt(), VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
        )

        val result = callRetryUp(
            configuration,
            retryTarget(MigrationVersionState.STATUS_MIGRATE_STARTED, checkpoint, secondRegistration),
            toCompleteVersion = true,
        )

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:second"), VersionMigrationTrace.events)
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
    }

    @Test
    fun `retry UP rejects EXECUTION_IN_FLIGHT and stale revision before side effects`() {
        val configuration = retryVersionConfiguration(first = true)
        val inFlight = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            1,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_IN_FLIGHT,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(inFlight))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )
        val inFlightResult = callRetryUp(
            configuration,
            retryTarget(
                MigrationVersionState.STATUS_MIGRATE_STARTED,
                inFlight,
                firstRegistration,
                MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED,
            ),
            toCompleteVersion = true,
        )
        assertIs<ExecuteResult.Failure>(inFlightResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(1, database.events.count { it == "lock-acquire" })

        database = FakeDatabase()
        scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
        val current = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            7,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_STARTED, null, checkpointJson(current))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )
        val stale = retryTarget(MigrationVersionState.STATUS_MIGRATE_STARTED, current.copy(revision = 6), firstRegistration)

        val staleResult = callRetryUp(configuration, stale, toCompleteVersion = true)

        assertIs<ExecuteResult.Failure>(staleResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(1, database.events.count { it == "lock-acquire" })
    }

    @Test
    fun `explicit retryUp and retryDown reexecute validated execution failures`() {
        for (up in listOf(true, false)) {
            database = FakeDatabase()
            scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
            VersionMigrationTrace.reset()
            val startedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_STARTED
            else MigrationVersionState.STATUS_ROLLBACK_STARTED
            val failureStatus = if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_UP
            else MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN
            val migrationId = VersionItemFirst.value
            val diagnostic = MigrationFailureDiagnostic(
                stage = if (up) MigrationFailureDiagnosticStage.UP_EXECUTION
                else MigrationFailureDiagnosticStage.DOWN_EXECUTION,
                exceptionClassName = "java.sql.SQLException",
                sqlState = "42S21",
                vendorErrorCode = 1060,
                message = "Duplicate column name",
                migrationId = migrationId,
            ).toStorageMessage(VERSION_ID_ONE)
            val checkpoint = retryCheckpoint(
                startedStatus,
                4,
                migrationId,
                MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                originalMessage = diagnostic,
            )
            val responses = mutableListOf(
                listOf(versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    failureStatus,
                    diagnostic,
                    checkpointJson(checkpoint),
                )),
                listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            )
            if (!up) {
                responses += listOf(tag(MIGRATION_TAG_ID, migrationId, MIGRATION_TAG_TYPE))
                responses += listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE))
            }
            database.respond(*responses.toTypedArray())

            val target = retryTarget(
                failureStatus,
                checkpoint,
                firstRegistration,
                if (up) MigrationRecoveryOperation.UP_EXECUTION
                else MigrationRecoveryOperation.DOWN_EXECUTION,
                diagnostic,
            )
            val result = if (up) {
                callRetryUp(retryVersionConfiguration(first = true), target, toCompleteVersion = true)
            } else {
                callRetryDown(retryVersionConfiguration(first = true), target, toCompleteVersion = true)
            }

            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result, "UP=$up").result)
            assertEquals(listOf(if (up) "up:first" else "down:first"), VersionMigrationTrace.events)
            assertTrue(database.events.any { it.startsWith("ddl:") }, "UP=$up must retry Schema explicitly")
            assertEquals(failureStatus, database.dmlCalls.first().parameters[2])
            assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED in database.versionStatuses.values, up)
            assertEquals(VERSION_ID_ONE !in database.versionStatuses, !up)
            assertEquals(1, database.events.count { it == "lock-acquire" }, "UP=$up")
        }
    }

    @Test
    fun `execution retry rejects all manual recovery statuses in both directions`() {
        val configuration = retryVersionConfiguration(first = true)
        for (status in listOf(
            MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED,
            MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED,
        )) {
            for (up in listOf(true, false)) {
                database = FakeDatabase()
                scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
                VersionMigrationTrace.reset()
                val originalStatus = if (status == MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED) {
                    MigrationVersionState.STATUS_MIGRATE_STARTED
                } else {
                    MigrationVersionState.STATUS_ROLLBACK_STARTED
                }
                val isUp = originalStatus == MigrationVersionState.STATUS_MIGRATE_STARTED
                val diagnostic = MigrationFailureDiagnostic(
                    stage = if (isUp) MigrationFailureDiagnosticStage.UP_EXECUTION
                    else MigrationFailureDiagnosticStage.DOWN_EXECUTION,
                    exceptionClassName = "java.sql.SQLException",
                    sqlState = "42S21",
                    vendorErrorCode = 1060,
                    message = "schema failure",
                    migrationId = VersionItemFirst.value,
                ).toStorageMessage(VERSION_ID_ONE)
                val checkpoint = retryCheckpoint(
                    originalStatus,
                    5,
                    VersionItemFirst.value,
                    MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                    originalMessage = diagnostic,
                )
                database.respond(
                    listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, status, diagnostic, checkpointJson(checkpoint))),
                    listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
                )
                val target = retryTarget(
                    status,
                    checkpoint,
                    firstRegistration,
                    if (isUp) MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED
                    else MigrationRecoveryOperation.DOWN_MANUAL_RECOVERY_REQUIRED,
                    diagnostic,
                )

                val result = if (up) callRetryUp(configuration, target, true) else callRetryDown(configuration, target, true)

                assertIs<ExecuteResult.Failure>(result, "status=$status up=$up")
                assertTrue(VersionMigrationTrace.events.isEmpty())
                assertTrue(database.events.none { it.startsWith("ddl:") })
                assertEquals(0, database.dmlCount)
            }
        }
    }

    @Test
    fun `explicit execution Retry CAS must be applied before Schema and stops for every other outcome`() {
        for (up in listOf(true, false)) {
            for (outcome in listOf("not-applied", "indeterminate", "query-failure")) {
                database = FakeDatabase()
                scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
                VersionMigrationTrace.reset()
                val startedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_STARTED
                else MigrationVersionState.STATUS_ROLLBACK_STARTED
                val failureStatus = if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_UP
                else MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN
                val stage = if (up) MigrationFailureDiagnosticStage.UP_EXECUTION
                else MigrationFailureDiagnosticStage.DOWN_EXECUTION
                val diagnostic = MigrationFailureDiagnostic(
                    stage = stage,
                    exceptionClassName = "java.sql.SQLException",
                    sqlState = "42S21",
                    vendorErrorCode = 1060,
                    message = "schema failure",
                    migrationId = VersionItemFirst.value,
                ).toStorageMessage(VERSION_ID_ONE)
                val checkpoint = retryCheckpoint(
                    startedStatus,
                    8,
                    VersionItemFirst.value,
                    MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                    originalMessage = diagnostic,
                )
                val responses = mutableListOf(
                    listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, failureStatus, diagnostic, checkpointJson(checkpoint))),
                    listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
                )
                if (!up) {
                    responses += listOf(tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE))
                    responses += listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE))
                }
                database.respond(*responses.toTypedArray())
                when (outcome) {
                    "not-applied" -> database.updateCountWhen = { call ->
                        if (isExecutionNormalizationCas(call)) 0 else null
                    }
                    "indeterminate" -> database.updateCountWhen = { call ->
                        if (isExecutionNormalizationCas(call)) 2 else null
                    }
                    "query-failure" -> database.failDmlWhen = { call ->
                        if (isExecutionNormalizationCas(call)) SQLException("execution normalization failed") else null
                    }
                }
                val target = retryTarget(
                    failureStatus,
                    checkpoint,
                    firstRegistration,
                    if (up) MigrationRecoveryOperation.UP_EXECUTION else MigrationRecoveryOperation.DOWN_EXECUTION,
                    diagnostic,
                )

                val result = if (up) {
                    callRetryUp(retryVersionConfiguration(first = true), target, true)
                } else {
                    callRetryDown(retryVersionConfiguration(first = true), target, true)
                }

                assertIs<ExecuteResult.Failure>(result, "$up/$outcome")
                assertTrue(VersionMigrationTrace.events.isEmpty(), "$up/$outcome")
                assertTrue(database.events.none { it.startsWith("ddl:") }, "$up/$outcome")
                assertEquals(1, database.dmlCalls.size, "Only the exact normalization CAS may run")
                assertTrue(isExecutionNormalizationCas(database.dmlCalls.single()))
                assertEquals(failureStatus, database.versionStatuses[VERSION_ID_ONE])
                assertEquals(checkpointJson(checkpoint), database.versionCheckpoints[VERSION_ID_ONE])
            }
        }
    }

    @Test
    fun `failed explicit execution Retry retains failure status and checkpoint cursor`() {
        for (up in listOf(true, false)) {
            database = FakeDatabase()
            scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
            VersionMigrationTrace.reset()
            val startedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_STARTED
            else MigrationVersionState.STATUS_ROLLBACK_STARTED
            val failedStatus = if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_UP
            else MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN
            val stage = if (up) MigrationFailureDiagnosticStage.UP_EXECUTION
            else MigrationFailureDiagnosticStage.DOWN_EXECUTION
            val originalDiagnostic = MigrationFailureDiagnostic(
                stage = stage,
                exceptionClassName = "java.sql.SQLException",
                sqlState = "42000",
                vendorErrorCode = 1060,
                message = "original schema failure",
                migrationId = RetryFailureItem.value,
            ).toStorageMessage(VERSION_ID_ONE)
            val checkpoint = retryCheckpoint(
                startedStatus,
                11,
                RetryFailureItem.value,
                MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                originalMessage = originalDiagnostic,
            )
            val configuration = migrationConfig {
                migrationVersion(VersionTag) { migration<RetryFailingMigration>() }
            }
            val registration = registered(RetryFailureItem, RetryFailingMigration::class)
            val responses = mutableListOf(
                listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, failedStatus, originalDiagnostic, checkpointJson(checkpoint))),
                listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            )
            if (!up) {
                responses += listOf(tag(MIGRATION_TAG_ID, RetryFailureItem.value, MIGRATION_TAG_TYPE))
                responses += listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE))
            }
            database.respond(*responses.toTypedArray())
            val target = retryTarget(
                failedStatus,
                checkpoint,
                registration,
                if (up) MigrationRecoveryOperation.UP_EXECUTION else MigrationRecoveryOperation.DOWN_EXECUTION,
                originalDiagnostic,
            )

            val result = if (up) callRetryUp(configuration, target, true) else callRetryDown(configuration, target, true)

            assertIs<ExecuteResult.Failure>(result)
            assertEquals(listOf(if (up) "retry:up" else "retry:down"), VersionMigrationTrace.events)
            assertEquals(failedStatus, database.versionStatuses[VERSION_ID_ONE])
            val failureWrite = failureStatusCalls().single()
            val updated = MigrationRetryCheckpointCodec.decode(failureWrite.parameters[2] as String)
            assertEquals(MigrationRetryPhase.EXECUTION_IN_FLIGHT, updated.phase)
            assertEquals(RetryFailureItem.value, updated.cursorMigrationId)
            assertEquals(checkpoint.revision + 1, updated.revision)
            assertEquals(originalDiagnostic, updated.originalMessage)
            assertTrue((failureWrite.parameters[1] as String).contains("retry:up".takeIf { up } ?: "retry:down"))
        }
    }

    @Test
    fun `retry DOWN RECORD_PENDING reconciles history without executing down schema`() {
        val configuration = retryVersionConfiguration(first = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            2,
            VersionItemFirst.value,
            MigrationRetryPhase.RECORD_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_ROLLBACK_STARTED, null, checkpointJson(checkpoint))),
            listOf(
                tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
                tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            listOf(
                tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
                tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            listOf(versionSnapshotRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_ROLLBACK_STARTED, checkpointJson(checkpoint))),
            listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE)),
        )

        val result = callRetryDown(
            retryVersionConfiguration(first = true),
            retryTarget(MigrationVersionState.STATUS_ROLLBACK_STARTED, checkpoint, firstRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
        val paused = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.FINALIZATION_PENDING, paused.phase)
        assertEquals(MigrationVersionState.STATUS_ROLLBACK_STARTED, database.versionStatuses[VERSION_ID_ONE])
        assertEquals(1, database.events.count { it == "lock-acquire" })
    }

    @Test
    fun `retry DOWN recording failure normalizes before metadata-only reconciliation`() {
        val diagnostic = retryRecordingDiagnostic(up = false, migrationId = VersionItemFirst.value)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            4,
            VersionItemFirst.value,
            MigrationRetryPhase.RECORD_PENDING,
            originalMessage = diagnostic,
        )
        database.respond(
            listOf(versionStateRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                diagnostic,
                checkpointJson(checkpoint),
            )),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            listOf(
                tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
                tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            listOf(versionSnapshotRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_ROLLBACK_STARTED,
                checkpointJson(checkpoint),
            )),
            listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE)),
        )

        val result = callRetryDown(
            retryVersionConfiguration(first = true),
            retryTarget(
                MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                checkpoint,
                firstRegistration,
                MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION,
                diagnostic,
            ),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertTrue(VersionMigrationTrace.events.isEmpty(), "Recording recovery must not invoke down()")
        assertEquals(MigrationVersionState.STATUS_ROLLBACK_STARTED, database.versionStatuses[VERSION_ID_ONE])
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
        assertEquals(
            MigrationRetryPhase.FINALIZATION_PENDING,
            MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE])).phase,
        )
        val normalization = database.dmlCalls.first()
        assertTrue(normalization.sql.contains("SET status = ?", ignoreCase = true), normalization.sql)
        assertEquals(MigrationVersionState.STATUS_ROLLBACK_STARTED, normalization.parameters[0])
        assertEquals(MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD, normalization.parameters[2])
        assertTrue(database.dmlCalls.indexOfFirst { it.sql.contains("DELETE", true) } > 0)
    }

    @Test
    fun `retry DOWN reconciliation inconsistency requires manual recovery status 210`() {
        val diagnostic = retryRecordingDiagnostic(up = false, migrationId = VersionItemFirst.value)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            4,
            VersionItemFirst.value,
            MigrationRetryPhase.RECORD_PENDING,
            originalMessage = diagnostic,
        )
        database.respond(
            listOf(versionStateRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                diagnostic,
                checkpointJson(checkpoint),
            )),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            listOf(
                tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE),
                tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            listOf(versionSnapshotRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_ROLLBACK_STARTED,
                checkpointJson(checkpoint),
            )),
            listOf(history(APPLIED_ID, MIGRATION_TAG_ID + 1000, VERSION_ID_ONE)),
        )

        val result = callRetryDown(
            retryVersionConfiguration(first = true),
            retryTarget(
                MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                checkpoint,
                firstRegistration,
                MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION,
                diagnostic,
            ),
            toCompleteVersion = false,
        )

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("dangling History reference"), failure.message.orEmpty())
        assertTrue(VersionMigrationTrace.events.isEmpty(), "Reconciliation must never invoke down()")
        assertEquals(0, database.deleteHistoryCount)
        assertEquals(0, database.deleteTagCount)
        assertStatusTransition(
            failureStatusCalls().single(),
            target = MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED,
            versionId = VERSION_ID_ONE,
            expected = MigrationVersionState.STATUS_ROLLBACK_STARTED,
            expectedStage = "DOWN_RECORDING",
            messageContains = "dangling History reference",
            migrationId = VersionItemFirst.value,
            checkpointPhase = MigrationRetryPhase.RECORD_PENDING,
            expectedOriginalMessage = diagnostic,
        )
    }

    @Test
    fun `retry UP and DOWN finalization failures normalize before finalization only`() {
        for (up in listOf(true, false)) {
            VersionMigrationTrace.reset()
            database = FakeDatabase()
            scope = OperationExecutionScope(
                database.connection,
                DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
            )
            val originalDiagnostic = retryRecordingDiagnostic(
                up,
                migrationId = VersionItemFirst.value,
            )
            val currentDiagnostic = retryRecordingDiagnostic(up, migrationId = null)
            val startedStatus = if (up) {
                MigrationVersionState.STATUS_MIGRATE_STARTED
            } else {
                MigrationVersionState.STATUS_ROLLBACK_STARTED
            }
            val failedStatus = if (up) {
                MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
            } else {
                MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
            }
            val checkpoint = retryCheckpoint(
                startedStatus,
                6,
                null,
                MigrationRetryPhase.FINALIZATION_PENDING,
                originalMessage = originalDiagnostic,
            )
            database.respond(
                listOf(versionStateRow(
                    VERSION_ID_ONE,
                    VERSION_TAG_ID_ONE,
                    failedStatus,
                    currentDiagnostic,
                    checkpointJson(checkpoint),
                )),
                listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            )
            val target = retryTarget(
                failedStatus,
                checkpoint,
                migration = null,
                operation = if (up) MigrationRecoveryOperation.UP_RECORDING_EXECUTION
                else MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION,
                message = currentDiagnostic,
            )

            val result = if (up) {
                callRetryUp(retryVersionConfiguration(first = true), target, toCompleteVersion = false)
            } else {
                callRetryDown(retryVersionConfiguration(first = true), target, toCompleteVersion = false)
            }

            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
            assertTrue(VersionMigrationTrace.events.isEmpty())
            assertEquals(2, database.dmlCalls.size, "Expected normalization followed by finalization")
            assertEquals(startedStatus, database.dmlCalls.first().parameters[0])
            assertTrue(
                if (up) database.dmlCalls.last().sql.contains("retry_checkpoint = NULL", true)
                else database.dmlCalls.last().sql.contains("DELETE", true),
                database.dmlCalls.last().sql,
            )
            assertTrue(database.dmlCalls.none(::isHistoryInsert))
            assertTrue(database.dmlCalls.none(::isHistoryDelete))
            assertEquals(0, database.deleteTagCount)
            if (up) {
                assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
                assertNull(database.versionCheckpoints[VERSION_ID_ONE])
            } else {
                assertTrue(!database.versionStatuses.containsKey(VERSION_ID_ONE))
                assertTrue(!database.versionCheckpoints.containsKey(VERSION_ID_ONE))
            }
        }
    }

    @Test
    fun `retry normalization validates diagnostics and stops before recording or finalization unless CAS applies`() {
        data class NormalizationCase(val up: Boolean, val phase: MigrationRetryPhase)
        val cases = listOf(
            NormalizationCase(up = true, MigrationRetryPhase.RECORD_PENDING),
            NormalizationCase(up = false, MigrationRetryPhase.RECORD_PENDING),
            NormalizationCase(up = true, MigrationRetryPhase.FINALIZATION_PENDING),
            NormalizationCase(up = false, MigrationRetryPhase.FINALIZATION_PENDING),
        )

        for (case in cases) {
            for (outcome in listOf("not-applied", "indeterminate", "query-failure")) {
                VersionMigrationTrace.reset()
                database = FakeDatabase()
                scope = OperationExecutionScope(
                    database.connection,
                    DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
                )
                val startedStatus = if (case.up) {
                    MigrationVersionState.STATUS_MIGRATE_STARTED
                } else {
                    MigrationVersionState.STATUS_ROLLBACK_STARTED
                }
                val failedStatus = if (case.up) {
                    MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
                } else {
                    MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
                }
                val cursor = if (case.phase == MigrationRetryPhase.FINALIZATION_PENDING) null
                else if (case.up) VersionItemFirst.value else VersionItemFirst.value
                val diagnostic = retryRecordingDiagnostic(case.up, cursor)
                val checkpoint = retryCheckpoint(
                    startedStatus,
                    8,
                    cursor,
                    case.phase,
                    originalMessage = diagnostic,
                )
                database.respond(
                    listOf(versionStateRow(
                        VERSION_ID_ONE,
                        VERSION_TAG_ID_ONE,
                        failedStatus,
                        diagnostic,
                        checkpointJson(checkpoint),
                    )),
                    listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
                )
                when (outcome) {
                    "not-applied" -> database.updateCountWhen = { call ->
                        if (isRecordingNormalizationCas(call)) 0 else null
                    }
                    "indeterminate" -> database.updateCountWhen = { call ->
                        if (isRecordingNormalizationCas(call)) 2 else null
                    }
                    "query-failure" -> database.failDmlWhen = { call ->
                        if (isRecordingNormalizationCas(call)) SQLException("normalization query failed") else null
                    }
                }
                val migration = if (cursor == null) null else firstRegistration
                val target = retryTarget(
                    failedStatus,
                    checkpoint,
                    migration,
                    if (case.up) MigrationRecoveryOperation.UP_RECORDING_EXECUTION
                    else MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION,
                    diagnostic,
                )

                val result = if (case.up) {
                    callRetryUp(retryVersionConfiguration(first = true), target, toCompleteVersion = true)
                } else {
                    callRetryDown(retryVersionConfiguration(first = true), target, toCompleteVersion = true)
                }

                assertIs<ExecuteResult.Failure>(result, "${case.up}/${case.phase}/$outcome")
                assertEquals(1, database.dmlCalls.size, "Only normalization may be attempted for $outcome")
                assertTrue(isRecordingNormalizationCas(database.dmlCalls.single()))
                assertTrue(VersionMigrationTrace.events.isEmpty())
                assertEquals(0, database.insertHistoryCount)
                assertEquals(0, database.deleteHistoryCount)
                assertEquals(0, database.deleteTagCount)
                assertEquals(failedStatus, database.versionStatuses[VERSION_ID_ONE])
                assertEquals(checkpointJson(checkpoint), database.versionCheckpoints[VERSION_ID_ONE])
            }
        }

        // A malformed latest Diagnostic is rejected while validating the snapshot, before CAS.
        database = FakeDatabase()
        scope = OperationExecutionScope(database.connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))
        val validDiagnostic = retryRecordingDiagnostic(up = true, migrationId = VersionItemFirst.value)
        val invalidCheckpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            9,
            VersionItemFirst.value,
            MigrationRetryPhase.RECORD_PENDING,
            originalMessage = validDiagnostic,
        )
        database.respond(
            listOf(versionStateRow(
                VERSION_ID_ONE,
                VERSION_TAG_ID_ONE,
                MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                "{malformed",
                checkpointJson(invalidCheckpoint),
            )),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )
        val invalidResult = callRetryUp(
            retryVersionConfiguration(first = true),
            retryTarget(
                MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                invalidCheckpoint,
                firstRegistration,
                MigrationRecoveryOperation.UP_RECORDING_EXECUTION,
                "{malformed",
            ),
            toCompleteVersion = true,
        )
        assertIs<ExecuteResult.Failure>(invalidResult)
        assertEquals(0, database.dmlCount, "Diagnostic validation must precede normalization")
        assertTrue(VersionMigrationTrace.events.isEmpty())
    }

    @Test
    fun `retry pre-schema and post-schema checkpoint CAS failures stop the corresponding later work`() {
        for (up in listOf(true, false)) {
            for (boundary in listOf(MigrationRetryPhase.EXECUTION_IN_FLIGHT, MigrationRetryPhase.RECORD_PENDING)) {
                for (outcome in listOf("not-applied", "indeterminate", "query-failure")) {
                    VersionMigrationTrace.reset()
                    database = FakeDatabase()
                    scope = OperationExecutionScope(
                        database.connection,
                        DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
                    )
                    val startedStatus = if (up) {
                        MigrationVersionState.STATUS_MIGRATE_STARTED
                    } else {
                        MigrationVersionState.STATUS_ROLLBACK_STARTED
                    }
                    val checkpoint = retryCheckpoint(
                        startedStatus,
                        0,
                        VersionItemFirst.value,
                        MigrationRetryPhase.EXECUTION_PENDING,
                    )
                    database.respond(
                        listOf(versionStateRow(
                            VERSION_ID_ONE,
                            VERSION_TAG_ID_ONE,
                            startedStatus,
                            null,
                            checkpointJson(checkpoint),
                        )),
                        listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
                    )
                    if (!up) {
                        database.respond(
                            listOf(tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE)),
                            listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE)),
                            emptyList(),
                        )
                    }
                    val failedBoundary = { call: FakeDatabase.DmlCall ->
                        isCheckpointUpdateTo(call, boundary, VersionItemFirst.value)
                    }
                    when (outcome) {
                        "not-applied" -> database.updateCountWhen = { call -> if (failedBoundary(call)) 0 else null }
                        "indeterminate" -> database.updateCountWhen = { call -> if (failedBoundary(call)) 2 else null }
                        "query-failure" -> database.failDmlWhen = { call ->
                            if (failedBoundary(call)) SQLException("${boundary.name} CAS query failed") else null
                        }
                    }
                    val result = if (up) {
                        callRetryUp(
                            retryVersionConfiguration(first = true),
                            retryTarget(startedStatus, checkpoint, firstRegistration),
                            toCompleteVersion = true,
                        )
                    } else {
                        callRetryDown(
                            retryVersionConfiguration(first = true),
                            retryTarget(startedStatus, checkpoint, firstRegistration),
                            toCompleteVersion = true,
                        )
                    }

                    assertIs<ExecuteResult.Failure>(result, "$up/$boundary/$outcome")
                    val expectedSchema = if (boundary == MigrationRetryPhase.RECORD_PENDING) {
                        listOf(if (up) "up:first" else "down:first")
                    } else {
                        emptyList()
                    }
                    assertEquals(expectedSchema, VersionMigrationTrace.events, "$up/$boundary/$outcome")
                    assertEquals(0, database.insertHistoryCount)
                    assertEquals(0, database.deleteHistoryCount)
                    assertEquals(0, database.deleteTagCount)
                    assertEquals(1, database.dmlCalls.count { failedBoundary(it) })
                    assertTrue(database.dmlCalls.none { it.sql.contains("retry_checkpoint = NULL", true) })
                    assertTrue(database.dmlCalls.none(::isVersionDelete))
                }
            }
        }
    }

    @Test
    fun `retry cursor advancement CAS failure prevents dispatching the next Migration`() {
        for (up in listOf(true, false)) {
            for (nextPhase in listOf(
                MigrationRetryPhase.EXECUTION_PENDING,
                MigrationRetryPhase.PAUSED_NEXT,
                MigrationRetryPhase.FINALIZATION_PENDING,
            )) {
                for (outcome in listOf("not-applied", "indeterminate", "query-failure")) {
                VersionMigrationTrace.reset()
                database = FakeDatabase()
                scope = OperationExecutionScope(
                    database.connection,
                    DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
                )
                val startedStatus = if (up) {
                    MigrationVersionState.STATUS_MIGRATE_STARTED
                } else {
                    MigrationVersionState.STATUS_ROLLBACK_STARTED
                }
                val hasNextMigration = nextPhase != MigrationRetryPhase.FINALIZATION_PENDING
                val cursor = if (up || !hasNextMigration) VersionItemFirst.value else VersionItemSecond.value
                val nextCursor = if (!hasNextMigration) null
                else if (up) VersionItemSecond.value else VersionItemFirst.value
                val migration = if (up || !hasNextMigration) firstRegistration else secondRegistration
                val checkpoint = retryCheckpoint(
                    startedStatus,
                    2,
                    cursor,
                    MigrationRetryPhase.EXECUTION_PENDING,
                )
                database.respond(
                    listOf(versionStateRow(
                        VERSION_ID_ONE,
                        VERSION_TAG_ID_ONE,
                        startedStatus,
                        null,
                        checkpointJson(checkpoint),
                    )),
                    listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
                )
                if (!up) {
                    database.respond(
                        listOf(tag(ITEM_TAG_ID_SECOND, VersionItemSecond.value, MIGRATION_TAG_TYPE)),
                        listOf(history(OTHER_APPLIED_ID, ITEM_TAG_ID_SECOND, VERSION_ID_ONE)),
                        emptyList(),
                    )
                }
                val cursorCas = { call: FakeDatabase.DmlCall ->
                    isCheckpointUpdateTo(call, nextPhase, nextCursor)
                }
                when (outcome) {
                    "not-applied" -> database.updateCountWhen = { call -> if (cursorCas(call)) 0 else null }
                    "indeterminate" -> database.updateCountWhen = { call -> if (cursorCas(call)) 2 else null }
                    "query-failure" -> database.failDmlWhen = { call ->
                        if (cursorCas(call)) SQLException("cursor advancement query failed") else null
                    }
                }

                val configuration = retryVersionConfiguration(first = true, second = hasNextMigration)
                val result = if (up) {
                    callRetryUp(
                        configuration,
                        retryTarget(startedStatus, checkpoint, migration),
                        toCompleteVersion = nextPhase == MigrationRetryPhase.EXECUTION_PENDING,
                    )
                } else {
                    callRetryDown(
                        configuration,
                        retryTarget(startedStatus, checkpoint, migration),
                        toCompleteVersion = nextPhase == MigrationRetryPhase.EXECUTION_PENDING,
                    )
                }

                assertIs<ExecuteResult.Failure>(result, "$up/$nextPhase/$outcome")
                val expectedMigration = if (up) "up:first" else if (hasNextMigration) "down:second" else "down:first"
                assertEquals(listOf(expectedMigration), VersionMigrationTrace.events)
                assertEquals(if (up) 1 else 0, database.insertHistoryCount)
                assertEquals(if (up) 0 else 1, database.dmlCalls.count(::isHistoryDelete))
                assertEquals(1, database.dmlCalls.count { cursorCas(it) })
                assertEquals(
                    if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
                    else MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                    failureStatusCalls().single().parameters[0],
                )
                assertTrue(database.dmlCalls.none { it.sql.contains("retry_checkpoint = NULL", true) })
                assertTrue(database.dmlCalls.none(::isVersionDelete))
                }
            }
        }
    }

    @Test
    fun `retry finalization CAS failure does not report completion for either direction`() {
        for (up in listOf(true, false)) {
            for (outcome in listOf("not-applied", "indeterminate", "query-failure")) {
                VersionMigrationTrace.reset()
                database = FakeDatabase()
                scope = OperationExecutionScope(
                    database.connection,
                    DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
                )
                val startedStatus = if (up) {
                    MigrationVersionState.STATUS_MIGRATE_STARTED
                } else {
                    MigrationVersionState.STATUS_ROLLBACK_STARTED
                }
                val checkpoint = retryCheckpoint(
                    startedStatus,
                    5,
                    null,
                    MigrationRetryPhase.FINALIZATION_PENDING,
                )
                database.respond(
                    listOf(versionStateRow(
                        VERSION_ID_ONE,
                        VERSION_TAG_ID_ONE,
                        startedStatus,
                        null,
                        checkpointJson(checkpoint),
                    )),
                    listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
                )
                val finalizationCas = { call: FakeDatabase.DmlCall ->
                    if (up) call.sql.contains("retry_checkpoint = NULL", true) &&
                        call.parameters.firstOrNull() == MigrationVersionState.STATUS_MIGRATE_COMPLETED
                    else isVersionDelete(call)
                }
                when (outcome) {
                    "not-applied" -> database.updateCountWhen = { call -> if (finalizationCas(call)) 0 else null }
                    "indeterminate" -> database.updateCountWhen = { call -> if (finalizationCas(call)) 2 else null }
                    "query-failure" -> database.failDmlWhen = { call ->
                        if (finalizationCas(call)) SQLException("finalization query failed") else null
                    }
                }
                val target = retryTarget(startedStatus, checkpoint, migration = null)
                val result = if (up) {
                    callRetryUp(retryVersionConfiguration(first = true), target, toCompleteVersion = false)
                } else {
                    callRetryDown(retryVersionConfiguration(first = true), target, toCompleteVersion = false)
                }

                assertIs<ExecuteResult.Failure>(result, "$up/$outcome")
                assertTrue(VersionMigrationTrace.events.isEmpty())
                assertEquals(0, database.insertHistoryCount)
                assertTrue(database.dmlCalls.none(::isHistoryDelete))
                assertEquals(0, database.deleteTagCount)
                assertTrue(finalizationCas(database.dmlCalls.first()))
                assertEquals(1, database.dmlCalls.count(::isFailureStatusWrite))
                assertEquals(
                    if (up) MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
                    else MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                    database.versionStatuses[VERSION_ID_ONE],
                )
                if (!up) assertTrue(database.versionStatuses.containsKey(VERSION_ID_ONE))
            }
        }
    }

    @Test
    fun `retry DOWN execution pending runs schema then records before pausing for finalization`() {
        val configuration = retryVersionConfiguration(first = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            0,
            VersionItemFirst.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_ROLLBACK_STARTED, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            listOf(tag(MIGRATION_TAG_ID, VersionItemFirst.value, MIGRATION_TAG_TYPE)),
            listOf(history(APPLIED_ID, MIGRATION_TAG_ID, VERSION_ID_ONE)),
            emptyList(),
        )

        val result = callRetryDown(
            configuration,
            retryTarget(MigrationVersionState.STATUS_ROLLBACK_STARTED, checkpoint, firstRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:first"), VersionMigrationTrace.events)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
        assertEquals(MigrationVersionState.STATUS_ROLLBACK_STARTED, database.versionStatuses[VERSION_ID_ONE])
        val finalization = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.FINALIZATION_PENDING, finalization.phase)
        assertNull(finalization.cursorMigrationId)
        assertEquals(1, database.events.count { it == "lock-acquire" })

        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_ROLLBACK_STARTED, null, checkpointJson(finalization))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )
        val finalized = callRetryDown(
            configuration,
            retryTarget(MigrationVersionState.STATUS_ROLLBACK_STARTED, finalization, migration = null),
            toCompleteVersion = false,
        )

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(finalized).result)
        assertEquals(listOf("down:first"), VersionMigrationTrace.events)
        assertTrue(!database.versionStatuses.containsKey(VERSION_ID_ONE))
        assertTrue(!database.versionCheckpoints.containsKey(VERSION_ID_ONE))
        assertEquals(2, database.events.count { it == "lock-acquire" })
    }

    @Test
    fun `retry DOWN false from execution pending pauses at the next cursor in reverse order`() {
        val configuration = retryVersionConfiguration(first = true, second = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            0,
            VersionItemSecond.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_ROLLBACK_STARTED, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            listOf(tag(ITEM_TAG_ID_SECOND, VersionItemSecond.value, MIGRATION_TAG_TYPE)),
            listOf(history(OTHER_APPLIED_ID, ITEM_TAG_ID_SECOND, VERSION_ID_ONE)),
            emptyList(),
        )

        val result = callRetryDown(
            configuration,
            retryTarget(MigrationVersionState.STATUS_ROLLBACK_STARTED, checkpoint, secondRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:second"), VersionMigrationTrace.events)
        assertEquals(1, database.deleteHistoryCount)
        val paused = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.PAUSED_NEXT, paused.phase)
        assertEquals(VersionItemFirst.value, paused.cursorMigrationId)
    }

    @Test
    fun `retry DOWN false from paused next executes stored reverse cursor once and pauses at the next cursor`() {
        val configuration = migrationConfig {
            migrationVersion(VersionTag) {
                migration<VersionFirstMigration>()
                migration<VersionSecondMigration>()
                migration<VersionThirdMigration>()
            }
        }
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            3,
            VersionItemSecond.value,
            MigrationRetryPhase.PAUSED_NEXT,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_ROLLBACK_STARTED, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            listOf(tag(ITEM_TAG_ID_SECOND, VersionItemSecond.value, MIGRATION_TAG_TYPE)),
            listOf(history(OTHER_APPLIED_ID, ITEM_TAG_ID_SECOND, VERSION_ID_ONE)),
            emptyList(),
        )

        val result = callRetryDown(
            configuration,
            retryTarget(MigrationVersionState.STATUS_ROLLBACK_STARTED, checkpoint, secondRegistration),
            toCompleteVersion = false,
        )

        assertEquals(false, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:second"), VersionMigrationTrace.events)
        assertEquals(1, database.deleteHistoryCount)
        val paused = MigrationRetryCheckpointCodec.decode(requireNotNull(database.versionCheckpoints[VERSION_ID_ONE]))
        assertEquals(MigrationRetryPhase.PAUSED_NEXT, paused.phase)
        assertEquals(VersionItemFirst.value, paused.cursorMigrationId)
    }

    @Test
    fun `retry DOWN to complete version resumes cursor then follows reverse declaration order`() {
        val configuration = retryVersionConfiguration(first = true, second = true)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            3,
            VersionItemSecond.value,
            MigrationRetryPhase.EXECUTION_PENDING,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_ROLLBACK_STARTED, null, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            listOf(tag(ITEM_TAG_ID_SECOND, VersionItemSecond.value, MIGRATION_TAG_TYPE)),
            listOf(history(APPLIED_ID, ITEM_TAG_ID_SECOND, VERSION_ID_ONE)),
            emptyList(),
            listOf(tag(ITEM_TAG_ID_FIRST, VersionItemFirst.value, MIGRATION_TAG_TYPE)),
            listOf(history(OTHER_APPLIED_ID, ITEM_TAG_ID_FIRST, VERSION_ID_ONE)),
            emptyList(),
        )

        val result = callRetryDown(
            configuration,
            retryTarget(MigrationVersionState.STATUS_ROLLBACK_STARTED, checkpoint, secondRegistration),
            toCompleteVersion = true,
        )

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:second", "down:first"), VersionMigrationTrace.events)
        assertEquals(
            2,
            database.dmlCalls.count { call ->
                call.sql.contains("DELETE", ignoreCase = true) &&
                    call.sql.contains("system_migration", ignoreCase = true) &&
                    call.parameters.size == 2
            },
        )
        assertEquals(2, database.deleteTagCount)
        assertTrue(!database.versionStatuses.containsKey(VERSION_ID_ONE))
        assertTrue(!database.versionCheckpoints.containsKey(VERSION_ID_ONE))
    }

    @Test
    fun `retry UP recording failure stops when normalization CAS is not applied or indeterminate`() {
        val configuration = retryVersionConfiguration(first = true)
        val diagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_RECORDING,
            exceptionClassName = "java.lang.IllegalStateException",
            sqlState = null,
            vendorErrorCode = null,
            message = "recording failed",
            migrationId = VersionItemFirst.value,
        ).toStorageMessage(VERSION_ID_ONE)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            4,
            VersionItemFirst.value,
            MigrationRetryPhase.RECORD_PENDING,
            originalMessage = diagnostic,
        )

        for (updateCount in listOf(0, 2)) {
            VersionMigrationTrace.reset()
            database = FakeDatabase()
            scope = OperationExecutionScope(
                database.connection,
                DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
            )
            database.respond(
                listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, diagnostic, checkpointJson(checkpoint))),
                listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
            )
            database.respondUpdateCounts(updateCount)

            val result = callRetryUp(
                configuration,
                retryTarget(
                    MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                    checkpoint,
                    firstRegistration,
                    MigrationRecoveryOperation.UP_RECORDING_EXECUTION,
                    diagnostic,
                ),
                toCompleteVersion = true,
            )

            assertIs<ExecuteResult.Failure>(result)
            assertTrue(VersionMigrationTrace.events.isEmpty())
            assertEquals(1, database.dmlCalls.size, "normalization must be the last write for CAS result $updateCount")
            assertEquals(MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, database.versionStatuses[VERSION_ID_ONE])
            assertEquals(checkpointJson(checkpoint), database.versionCheckpoints[VERSION_ID_ONE])
        }
    }

    @Test
    fun `retry UP recording failure normalizes via exact checkpoint and does not execute schema`() {
        val configuration = retryVersionConfiguration(first = true)
        val diagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_RECORDING,
            exceptionClassName = "java.lang.IllegalStateException",
            sqlState = null,
            vendorErrorCode = null,
            message = "recording failed",
            migrationId = VersionItemFirst.value,
        ).toStorageMessage(VERSION_ID_ONE)
        val checkpoint = retryCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            4,
            VersionItemFirst.value,
            MigrationRetryPhase.RECORD_PENDING,
            originalMessage = diagnostic,
        )
        database.respond(
            listOf(versionStateRow(VERSION_ID_ONE, VERSION_TAG_ID_ONE, MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, diagnostic, checkpointJson(checkpoint))),
            listOf(tag(VERSION_TAG_ID_ONE, VersionTag.value, VERSION_TAG_TYPE)),
        )

        val result = callRetryUp(
            configuration,
            retryTarget(
                MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                checkpoint,
                firstRegistration,
                MigrationRecoveryOperation.UP_RECORDING_EXECUTION,
                diagnostic,
            ),
            toCompleteVersion = true,
        )

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(MigrationVersionState.STATUS_MIGRATE_COMPLETED, database.versionStatuses[VERSION_ID_ONE])
        assertTrue(database.dmlCalls.first().sql.contains("SET status = ?", ignoreCase = true))
        assertTrue(database.dmlCalls.first().sql.contains("retry_checkpoint", ignoreCase = true))
    }

    private fun callRetryUp(
        configuration: MigrationConfiguration,
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
    ): ExecuteResult<Boolean> = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
        MigrationController(configuration).retryUp(target, toCompleteVersion, activeScope)
    }

    private fun callRetryDown(
        configuration: MigrationConfiguration,
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
    ): ExecuteResult<Boolean> = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
        MigrationController(configuration).retryDown(target, toCompleteVersion, activeScope)
    }

    private fun callRecovery(
        configuration: MigrationConfiguration,
        toCompleteVersion: Boolean,
    ): ExecuteResult<Boolean> = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
        MigrationController(configuration).recovery(activeScope, toCompleteVersion)
    }

    private fun retryVersionConfiguration(first: Boolean, second: Boolean = false): MigrationConfiguration = migrationConfig {
        migrationVersion(VersionTag) {
            if (first) migration<VersionFirstMigration>()
            if (second) migration<VersionSecondMigration>()
        }
    }

    private fun retryCheckpoint(
        originalStatus: Int,
        revision: Long,
        cursor: String?,
        phase: MigrationRetryPhase,
        originalMessage: String? = null,
    ) = MigrationRetryCheckpoint(
        formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
        revision = revision,
        originalStatus = originalStatus,
        originalMessage = originalMessage,
        cursorMigrationId = cursor,
        phase = phase,
    )

    private fun retryRecordingDiagnostic(up: Boolean, migrationId: String?): String =
        MigrationFailureDiagnostic(
            stage = if (up) MigrationFailureDiagnosticStage.UP_RECORDING
            else MigrationFailureDiagnosticStage.DOWN_RECORDING,
            exceptionClassName = "java.lang.IllegalStateException",
            sqlState = null,
            vendorErrorCode = null,
            message = "recording failed",
            migrationId = migrationId,
        ).toStorageMessage(VERSION_ID_ONE)

    private fun checkpointJson(checkpoint: MigrationRetryCheckpoint): String =
        MigrationRetryCheckpointCodec.encode(checkpoint)

    private fun retryTarget(
        status: Int,
        checkpoint: MigrationRetryCheckpoint,
        migration: RegisteredMigration?,
        operation: MigrationRecoveryOperation = when {
            checkpoint.phase == MigrationRetryPhase.EXECUTION_IN_FLIGHT &&
                checkpoint.originalStatus == MigrationVersionState.STATUS_MIGRATE_STARTED ->
                MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED
            checkpoint.phase == MigrationRetryPhase.EXECUTION_IN_FLIGHT ->
                MigrationRecoveryOperation.DOWN_MANUAL_RECOVERY_REQUIRED
            checkpoint.phase in setOf(MigrationRetryPhase.EXECUTION_PENDING, MigrationRetryPhase.PAUSED_NEXT) ->
                MigrationRecoveryOperation.START_EXECUTION
            checkpoint.originalStatus == MigrationVersionState.STATUS_MIGRATE_STARTED ->
                MigrationRecoveryOperation.UP_RECORDING_EXECUTION
            else -> MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION
        },
        message: String? = checkpoint.originalMessage,
    ) = MigrationRecoveryTarget(
        MigrationVersionState(VERSION_ID_ONE, VERSION_TAG_ID_ONE, status, message),
        migration,
        operation,
        checkpoint.revision,
    )

    private fun readRecoveryTarget(
        configuration: MigrationConfiguration,
        selectedRows: List<Map<String, Any?>>,
        snapshotTags: List<Map<String, Any?>>,
        snapshotVersions: List<Map<String, Any?>>,
        selection: MigrationVersionSelection = MigrationVersionSelection.LATEST,
        versionId: Int? = null,
    ): Pair<ExecuteResult<MigrationRecoveryTarget?>, FakeDatabase> {
        database = FakeDatabase()
        scope = OperationExecutionScope(
            database.connection,
            DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
        )
        database.respond(selectedRows, snapshotTags, snapshotVersions, emptyList())
        val controller = MigrationController(configuration)

        val result = OperationExecutionExecutor(versionTestContext()).connection { activeScope ->
            if (versionId == null) {
                controller.state(activeScope, selection)
            } else {
                controller.state(activeScope, versionId)
            }
        }
        return result to database
    }

    private fun versionStateRow(id: Int, tagId: Int, status: Int, message: String?, retryCheckpoint: String? = null) =
        mapOf(
            "id" to id,
            "tagId" to tagId,
            "status" to status,
            "message" to message,
            "retryCheckpoint" to retryCheckpoint,
        )

    private fun versionSnapshotRow(id: Int, tagId: Int, status: Int, retryCheckpoint: String? = null) =
        mapOf("id" to id, "tagId" to tagId, "status" to status, "retryCheckpoint" to retryCheckpoint)

    private fun versionService(configuration: MigrationConfiguration): VersionTransactionHarness {
        return VersionTransactionHarness(
            MigrationVersionTransactionService(configuration),
            OperationExecutionExecutor(versionTestContext()),
        )
    }

    private fun versionTestContext(withIdentity: Boolean = true): DatabaseContext {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://migration-test.local",
                dbPort = 3306,
                dbName = "migration_test",
                dbUserName = "test-user",
                dbPassword = "test-password",
            ),
            platform = DatabasePlatformConfig(DialectQuery.MY_SQL),
        )
        val identity = if (withIdentity) DatabaseIdentity.from(config) else null
        return DatabaseContext(
            "migration-version-test",
            config,
            object : IDatabaseConnection {
                override fun build(): Connection {
                    database.connectionBuildCount++
                    return database.connection
                }
            },
            identity,
        )
    }

    /** The caller-owned Operation scope supplies the Connection for each Version operation. */
    private class VersionTransactionHarness(
        private val service: MigrationVersionTransactionService,
        private val callerExecutor: OperationExecutionExecutor,
    ) {
        fun transactionUp(versionTag: MigrationVersionTag): ExecuteResult<Boolean> =
            callerExecutor.connection { scope -> service.transactionUp(scope, versionTag) }

        fun transactionDown(versionTag: MigrationVersionTag): ExecuteResult<Boolean> =
            callerExecutor.connection { scope -> service.transactionDown(scope, versionTag) }
    }

    private class ThrowingMessageException(
        private val metadataFailure: Throwable,
    ) : RuntimeException() {
        override val message: String?
            get() = throw metadataFailure
    }

    private class ThrowingSqlStateException(
        private val metadataFailure: RuntimeException,
    ) : SQLException("driver failure", "42S21", 1060) {
        override fun getSQLState(): String? = throw metadataFailure
    }

    private fun versionConfiguration(
        vararg migrations: RegisteredMigration,
        versionName: String = VersionTag.validatedValue,
    ) = MigrationConfiguration(
        listOf(MigrationVersion(versionName, migrations.map(::SingleMigration))),
    )

    private val firstRegistration = registered(VersionItemFirst, VersionFirstMigration::class)
    private val secondRegistration = registered(VersionItemSecond, VersionSecondMigration::class)
    private val thirdRegistration = registered(VersionItemThird, VersionThirdMigration::class)

    private fun registered(tag: MigrationItemTag, type: kotlin.reflect.KClass<out Migration>) =
        RegisteredMigration(MigrationIdentity.fromValue(tag.value), type)

    private fun rows(
        id: Int,
        tagId: Int? = null,
        tagType: Int? = null,
        versionId: Int? = null,
        otherVersionId: Int? = null,
        status: Int? = null,
    ): List<Map<String, Any?>> = listOfNotNull(
        mapOfNotNull(
            "id" to id,
            "tagId" to tagId,
            "tagType" to tagType,
            "versionId" to versionId,
            "status" to status,
        ),
        otherVersionId?.let { mapOf("id" to OTHER_APPLIED_ID, "versionId" to it) },
    )

    private fun versionTagRow(id: Int): List<Map<String, Any?>> =
        listOf(tag(id, VersionTag.value, VERSION_TAG_TYPE))

    private fun tagRows(vararg tags: Map<String, Any?>): List<Map<String, Any?>> = tags.toList()

    private fun tag(id: Int, name: String, type: Int): Map<String, Any?> =
        mapOf("id" to id, "tagName" to name, "tagType" to type)

    private fun historyRows(vararg records: Map<String, Any?>): List<Map<String, Any?>> = records.toList()

    private fun history(id: Int, tagId: Int, versionId: Int): Map<String, Any?> =
        mapOf("id" to id, "tagId" to tagId, "versionId" to versionId)

    private fun mapOfNotNull(vararg values: Pair<String, Any?>): Map<String, Any?> =
        values.filter { it.second != null }.associate { it.first to it.second }

    private class FakeDatabase {
        val events = mutableListOf<String>()
        val executedSql = mutableListOf<String>()
        val failDmlCalls = mutableSetOf<Int>()
        val failDdlCalls = mutableSetOf<Int>()
        var failDmlWhen: ((DmlCall) -> Throwable?)? = null
        var updateCountWhen: ((DmlCall) -> Int?)? = null
        var applyDmlBeforeFailure: ((DmlCall) -> Boolean)? = null
        private val readResponses = ArrayDeque<List<Map<String, Any?>>>()
        private val updateCounts = ArrayDeque<Int>()
        private val persistedTags = mutableListOf<FakeTag>()
        private val persistedHistories = mutableListOf<FakeHistory>()
        var nextGeneratedId = 1000L
        val failReadCalls = mutableSetOf<Int>()
        private var readCount = 0
        private var lastGeneratedId = 0L
        private var ddlCount = 0

        val readParameters = mutableListOf<List<Any?>>()
        val dmlCalls = mutableListOf<DmlCall>()
        val dmlUpdateCounts = mutableListOf<Int?>()
        val lockHeldAtDmlCall = mutableListOf<Boolean>()
        val versionStatuses = mutableMapOf<Int, Int>()
        var readFailure: Throwable? = null
        var onVersionWrite: (() -> Unit)? = null
        var connectionBuildCount = 0
        private var connectionAutoCommit = true
        private var migrationLockHeld = false
        var autoCommitFalseCount = 0
            private set
        var autoCommitRestoreCount = 0
            private set
        val autoCommitAtPrepareStatement = mutableListOf<Boolean>()
        val lifecycleEvents = mutableListOf<String>()
        var generatedIdForVersionInsert: Long = 0
            private set
        val versionCheckpoints = mutableMapOf<Int, String?>()

        var dmlCount = 0
            private set
        var insertHistoryCount = 0
            private set
        var deleteCount = 0
            private set
        var deleteHistoryCount = 0
            private set
        var deleteTagCount = 0
            private set
        var connectionPreparationCount = 0
            private set
        var failDdl = false
        var systemLockAcquireValue: Int? = 1
        var systemLockReleaseValue: Int? = 1
        var systemLockAcquireFailure: SQLException? = null
        var systemLockReleaseFailure: SQLException? = null
        var statementCloseCount = 0
            private set
        var resultSetCloseCount = 0
            private set
        var connectionCloseCount = 0
            private set
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set

        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    connectionPreparationCount++
                    autoCommitAtPrepareStatement += connectionAutoCommit
                    val sql = args[0] as String
                    executedSql += sql
                    preparedStatement(sql)
                }
                "setAutoCommit" -> {
                    connectionAutoCommit = args[0] as Boolean
                    if (!connectionAutoCommit) {
                        autoCommitFalseCount++
                        lifecycleEvents += "autoCommit:false"
                    } else {
                        autoCommitRestoreCount++
                        lifecycleEvents += "autoCommit:true"
                    }
                    null
                }
                "getAutoCommit" -> connectionAutoCommit
                "commit" -> { commitCount++; lifecycleEvents += "commit"; events += "commit"; null }
                "rollback" -> { rollbackCount++; lifecycleEvents += "rollback"; events += "rollback"; null }
                "close" -> { connectionCloseCount++; lifecycleEvents += "close"; events += "connection-close"; null }
                "isClosed" -> false
                "toString" -> "Fake Migration Connection"
                else -> primitiveDefault(method.returnType)
            }
        }

        fun respond(vararg results: List<Map<String, Any?>>) {
            results.forEach(readResponses::addLast)
        }

        fun respondUpdateCounts(vararg counts: Int) {
            counts.forEach(updateCounts::addLast)
        }

        private fun preparedStatement(sql: String): PreparedStatement {
            val values = sortedMapOf<Int, Any?>()
            return proxy(PreparedStatement::class.java) { method, args ->
                when (method.name) {
                    "setObject", "setString", "setInt", "setLong", "setNull" -> {
                        values[args[0] as Int] = if (method.name == "setNull") null else args.getOrNull(1)
                        null
                    }
                    "executeQuery" -> {
                        val normalizedSql = sql.lowercase()
                        if ("get_lock(" in normalizedSql) {
                            events += "lock-acquire"
                            systemLockAcquireFailure?.let { throw it }
                            migrationLockHeld = systemLockAcquireValue == 1
                            return@proxy resultSet(listOf(mapOf("acquired" to systemLockAcquireValue)), sql)
                        }
                        if ("release_lock(" in normalizedSql) {
                            events += "lock-release"
                            systemLockReleaseFailure?.let { throw it }
                            if (systemLockReleaseValue == 1) migrationLockHeld = false
                            return@proxy resultSet(listOf(mapOf("released" to systemLockReleaseValue)), sql)
                        }
                        readCount++
                        events += "read:$sql"
                        readParameters += values.values.toList()
                        if (readCount in failReadCalls) {
                            throw readFailure ?: SQLException("Read $readCount failed")
                        }
                        val queuedResponse = if (readResponses.isEmpty()) emptyList() else readResponses.removeFirst()
                        val response = queuedResponse.ifEmpty {
                            simulatedSystemRows(normalizedSql, values.values.toList())
                        }
                        if ("system_migration_version" in normalizedSql) rememberVersions(response)
                        resultSet(
                            if ("system_migration_version" in normalizedSql) {
                                selectVersionRows(normalizedSql, response, values.values.toList())
                            } else {
                                response
                            },
                            sql,
                        )
                    }
                    "executeUpdate" -> executeUpdate(sql, values.values.toList())
                    "execute" -> {
                        events += "ddl:$sql"
                        ddlCount++
                        if (failDdl || ddlCount in failDdlCalls) throw SQLException("DDL $ddlCount failed")
                        true
                    }
                    "getGeneratedKeys" -> resultSet(listOf(mapOf("key" to lastGeneratedId)))
                    "close" -> { statementCloseCount++; null }
                    "isClosed" -> false
                    "toString" -> "Fake Migration PreparedStatement"
                    else -> primitiveDefault(method.returnType)
                }
            }
        }

        private fun executeUpdate(sql: String, values: List<Any?>): Int {
            dmlCount++
            val lower = sql.lowercase()
            val call = DmlCall(sql, values)
            dmlCalls += call
            dmlUpdateCounts += null
            val callIndex = dmlCalls.lastIndex
            lockHeldAtDmlCall += migrationLockHeld
            if (lower.contains("system_migration_version")) onVersionWrite?.invoke()
            if (lower.contains("insert into") && lower.contains("system_migration_version")) {
                lastGeneratedId = nextGeneratedId++
                generatedIdForVersionInsert = lastGeneratedId
            }
            when {
                lower.contains("insert into") && lower.contains("system_migration_tag") -> {
                    events += "insert-tag:$sql"
                    lastGeneratedId = nextGeneratedId++
                }
                lower.contains("insert into") && lower.contains("system_migration") &&
                    !lower.contains("system_migration_version") -> {
                    events += "insert-history:$sql"
                    insertHistoryCount++
                    if (!lower.contains("system_migration_version")) lastGeneratedId = nextGeneratedId++
                }
                lower.contains("delete") && lower.contains("system_migration_tag") -> {
                    events += "delete-tag:$sql"
                    deleteTagCount++
                    deleteCount++
                }
                lower.contains("delete") && lower.contains("system_migration") -> {
                    events += "delete-history:$sql"
                    deleteHistoryCount++
                    deleteCount++
                }
                else -> events += "dml:$sql"
            }
            val injectedFailure = failDmlWhen?.invoke(call)
                ?: if (dmlCount in failDmlCalls) SQLException("DML $dmlCount failed") else null
            if (injectedFailure != null) {
                if (applyDmlBeforeFailure?.invoke(call) == true) applyFakeDmlState(call)
                throw injectedFailure
            }

            val returnedCount = updateCountWhen?.invoke(call)
                ?: if (updateCounts.isEmpty()) 1 else updateCounts.removeFirst()
            val effectiveCount = conditionalCount(call, returnedCount)
            if (effectiveCount == 1) applyFakeDmlState(call)
            dmlUpdateCounts[callIndex] = effectiveCount
            return effectiveCount
        }

        private fun conditionalCount(call: DmlCall, returnedCount: Int): Int {
            val lower = call.sql.lowercase()
            if (lower.contains("system_migration_version") && lower.contains("update")) {
                val values = call.parameters
                val (versionIdIndex, expectedStatusIndex, expectedCheckpointIndex) = when {
                    values.size == 6 -> Triple(3, 4, 5) // Failure Status + Diagnostic + Checkpoint CAS.
                    values.size == 4 && lower.contains("retry_checkpoint is null") -> Triple(2, 3, null)
                    values.size == 4 && lower.contains("cast(retry_checkpoint as binary)") ->
                        if (lower.contains("set retry_checkpoint = ?")) Triple(1, 2, 3)
                        else Triple(1, 2, 3) // Finalize UP.
                    else -> return returnedCount
                }
                val versionId = values.getOrNull(versionIdIndex) as? Int ?: return 0
                val expectedStatus = values.getOrNull(expectedStatusIndex) as? Int ?: return 0
                if (versionStatuses[versionId] != expectedStatus) return 0
                if (expectedCheckpointIndex != null &&
                    versionCheckpoints[versionId] != values.getOrNull(expectedCheckpointIndex)
                ) return 0
                if (lower.contains("retry_checkpoint is null") && versionCheckpoints[versionId] != null) return 0
                return returnedCount
            }
            if (lower.contains("system_migration_version") && lower.contains("delete") && call.parameters.size == 3) {
                val versionId = call.parameters.getOrNull(0) as? Int ?: return 0
                val expectedStatus = call.parameters.getOrNull(1) as? Int ?: return 0
                val expectedCheckpoint = call.parameters.getOrNull(2)
                return if (versionStatuses[versionId] == expectedStatus &&
                    versionCheckpoints[versionId] == expectedCheckpoint
                ) returnedCount else 0
            }
            return returnedCount
        }

        private fun applyFakeDmlState(call: DmlCall) {
            val lower = call.sql.lowercase()
            when {
                lower.contains("insert into") && lower.contains("system_migration_tag") -> {
                    val tagId = lastGeneratedId.toInt()
                    persistedTags += FakeTag(
                        id = tagId,
                        name = call.parameters.getOrNull(0) as? String ?: return,
                        type = call.parameters.getOrNull(1) as? Int ?: return,
                    )
                }
                lower.contains("insert into") && lower.contains("system_migration") &&
                    !lower.contains("system_migration_version") -> {
                    persistedHistories += FakeHistory(
                        id = lastGeneratedId.toInt(),
                        tagId = call.parameters.getOrNull(0) as? Int ?: return,
                        versionId = call.parameters.getOrNull(1) as? Int ?: return,
                    )
                }
                lower.contains("insert into") && lower.contains("system_migration_version") -> {
                    versionStatuses[lastGeneratedId.toInt()] = call.parameters.getOrNull(1) as? Int
                        ?: MigrationVersionState.STATUS_MIGRATE_STARTED
                    versionCheckpoints[lastGeneratedId.toInt()] = call.parameters.getOrNull(3) as? String
                }
                lower.contains("update") && lower.contains("system_migration_version") -> {
                    val targetStatus = call.parameters.getOrNull(0) as? Int
                    val versionIdIndex = when {
                        call.parameters.size == 6 -> 3
                        call.parameters.size == 4 && lower.contains("retry_checkpoint is null") -> 2
                        call.parameters.size == 4 -> 1
                        else -> 1
                    }
                    val versionId = call.parameters.getOrNull(versionIdIndex) as? Int ?: return
                    val expectedStatusIndex = when {
                        call.parameters.size == 6 -> 4
                        call.parameters.size == 4 && lower.contains("retry_checkpoint is null") -> 3
                        call.parameters.size == 4 -> 2
                        else -> 2
                    }
                    if (versionStatuses[versionId] == call.parameters.getOrNull(expectedStatusIndex)) {
                        if (lower.contains("set status = ?") && targetStatus != null) {
                            versionStatuses[versionId] = targetStatus
                        }
                        when {
                            lower.contains("set status = ?, message = ?, retry_checkpoint = ?") ->
                                versionCheckpoints[versionId] = call.parameters.getOrNull(2) as? String
                            lower.contains("set status = ?, retry_checkpoint = ?") ->
                                versionCheckpoints[versionId] = call.parameters.getOrNull(1) as? String
                            lower.contains("retry_checkpoint = null") -> versionCheckpoints[versionId] = null
                            lower.contains("set retry_checkpoint = ?") ->
                                versionCheckpoints[versionId] = call.parameters.getOrNull(0) as? String
                        }
                    }
                }
                lower.contains("delete") && lower.contains("system_migration_version") -> {
                    val versionId = call.parameters.firstOrNull() as? Int ?: return
                    versionStatuses.remove(versionId)
                    versionCheckpoints.remove(versionId)
                }
                lower.contains("delete") && lower.contains("system_migration_tag") -> {
                    val tagId = call.parameters.firstOrNull() as? Int ?: return
                    persistedTags.removeAll { it.id == tagId }
                }
                lower.contains("delete") && lower.contains("system_migration") -> {
                    val tagId = call.parameters.getOrNull(0) as? Int ?: return
                    val versionId = call.parameters.getOrNull(1) as? Int ?: return
                    persistedHistories.removeAll { it.tagId == tagId && it.versionId == versionId }
                }
            }
        }

        private fun simulatedSystemRows(sql: String, parameters: List<Any?>): List<Map<String, Any?>> {
            if (Regex("\\bfrom\\s+system_migration_tag\\b").containsMatchIn(sql)) {
                val name = parameters.filterIsInstance<String>().firstOrNull()
                val type = parameters.filterIsInstance<Int>().firstOrNull()
                return persistedTags
                    .filter { (name == null || it.name == name) && (type == null || it.type == type) }
                    .map { mapOf("id" to it.id, "tagName" to it.name, "tagType" to it.type) }
            }
            if (Regex("\\bfrom\\s+system_migration\\b").containsMatchIn(sql) &&
                "system_migration_tag" !in sql && "system_migration_version" !in sql
            ) {
                val tagId = parameters.filterIsInstance<Int>().firstOrNull()
                return persistedHistories
                    .filter { tagId == null || it.tagId == tagId }
                    .map { mapOf("id" to it.id, "tagId" to it.tagId, "versionId" to it.versionId) }
            }
            return emptyList()
        }

        private fun rememberVersions(rows: List<Map<String, Any?>>) {
            rows.forEach { row ->
                val id = (row["id"] ?: row.entries.firstOrNull { it.key.equals("id", true) }?.value) as? Int
                    ?: return@forEach
                val status = (row["status"] ?: row.entries.firstOrNull { it.key.equals("status", true) }?.value) as? Int
                if (status != null) versionStatuses[id] = status
                val checkpoint = row["retryCheckpoint"]
                    ?: row.entries.firstOrNull { it.key.equals("retry_checkpoint", true) }?.value
                    ?: row.entries.firstOrNull { it.key.equals("retryCheckpoint", true) }?.value
                if (checkpoint == null || checkpoint is String) versionCheckpoints[id] = checkpoint as? String
            }
        }

        private fun resultSet(rows: List<Map<String, Any?>>, statementSql: String = ""): ResultSet {
            var index = -1
            var lastValue: Any? = null
            return proxy(ResultSet::class.java) { method, args ->
                when (method.name) {
                    "getMetaData" -> gog.my_project.data_base_migration.executor.support
                        .systemMigrationResultSetMetadata(statementSql)
                    "next" -> ++index < rows.size
                    "getInt", "getLong", "getString", "getObject" -> {
                        val row = rows.getOrNull(index) ?: error("ResultSet cursor is not on a row")
                        val value = if (args[0] is Number) {
                            row.values.elementAtOrNull((args[0] as Number).toInt() - 1)
                        } else {
                            row.entries.firstOrNull { it.key.equals(args[0].toString(), ignoreCase = true) }?.value
                        }
                        lastValue = value
                        when (method.name) {
                            "getInt" -> (value as? Number)?.toInt() ?: 0
                            "getLong" -> (value as? Number)?.toLong() ?: 0L
                            "getString" -> value?.toString()
                            else -> value
                        }
                    }
                    "wasNull" -> lastValue == null
                    "close" -> { resultSetCloseCount++; null }
                    "isClosed" -> false
                    "toString" -> "Fake Migration ResultSet"
                    else -> primitiveDefault(method.returnType)
                }
            }
        }

        /** Mimics the database filtering and descending selection used by the state-reader CTE. */
        private fun selectVersionRows(
            sql: String,
            rows: List<Map<String, Any?>>,
            parameters: List<Any?>,
        ): List<Map<String, Any?>> {
            var selected = rows
            if (Regex("status\\s*(?:<>|!=)\\s*\\?").containsMatchIn(sql)) {
                val completedStatus = parameters.firstOrNull() as? Int
                selected = selected.filter { (it["status"] as? Number)?.toInt() != completedStatus }
            }
            if (Regex("smv\\.id\\s*=\\s*\\?").containsMatchIn(sql)) {
                val selectedId = parameters.firstOrNull() as? Int
                selected = selected.filter { (it["id"] as? Number)?.toInt() == selectedId }
            }
            return if ("order by" in sql && "limit 1" in sql) {
                selected.sortedByDescending { (it["id"] as? Number)?.toInt() ?: Int.MIN_VALUE }.take(1)
            } else {
                selected
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(type: Class<T>, invocation: (java.lang.reflect.Method, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                invocation(method, args ?: emptyArray())
            } as T

        private fun primitiveDefault(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }

        data class DmlCall(val sql: String, val parameters: List<Any?>)
        private data class FakeTag(val id: Int, val name: String, val type: Int)
        private data class FakeHistory(val id: Int, val tagId: Int, val versionId: Int)
    }

    private companion object {
        const val STATUS_ROLLBACK_STARTED = MigrationVersionState.STATUS_ROLLBACK_STARTED
        const val STATUS_MIGRATE_COMPLETED = MigrationVersionState.STATUS_MIGRATE_COMPLETED
        const val VERSION_ID = 7
        const val VERSION_TAG_TYPE = 0
        const val MIGRATION_TAG_ID = 21
        const val MIGRATION_TAG_TYPE = 1
        const val APPLIED_ID = 31
        const val OTHER_APPLIED_ID = 32
        const val OTHER_VERSION_ID = 8
        const val GENERATED_TAG_ID = 1000L
        const val VERSION_TAG_ID_ONE = 51
        const val VERSION_TAG_ID_TWO = 52
        const val VERSION_TAG_ID_THREE = 53
        const val VERSION_TAG_ID_FOUR = 54
        const val VERSION_ID_ONE = 101
        const val VERSION_ID_TWO = 202
        const val VERSION_ID_THREE = 303
        const val VERSION_ID_FOUR = 404
        const val ITEM_TAG_ID_FIRST = 61
        const val ITEM_TAG_ID_SECOND = 62
        const val ITEM_TAG_ID_THIRD = 63
        const val ITEM_TAG_ID_FOURTH = 64
        const val ITEM_TAG_ID_FIFTH = 65
    }
}

private object MigrationItem : MigrationItemTag {
    override val value: String = "run_one_migration"
}

private class SampleMigration : Migration(MigrationItem) {
    override fun up(): MigrationDefinition {
        upCalls++
        upFailure?.let { throw it }
        return definition()
    }

    override fun down(): MigrationDefinition {
        downCalls++
        downFailure?.let { throw it }
        return definition()
    }

    private fun definition() = MigrationDefinition(
        Schema.createTable {
            table { tableName("migration_run_test") }
            addColumn {
                name("id")
                dataType(SchemaDataType.IntType)
                notNull()
            }
        },
    )

    companion object {
        var upCalls = 0
        var downCalls = 0
        var upFailure: Throwable? = null
        var downFailure: Throwable? = null

        fun reset() {
            upCalls = 0
            downCalls = 0
            upFailure = null
            downFailure = null
        }
    }
}

private class PreparedInstanceMigration : Migration(MigrationItem) {
    init {
        PreparedMigrationTrace.constructorCalls++
        PreparedMigrationTrace.constructedInstance = this
    }

    override fun up(): MigrationDefinition {
        PreparedMigrationTrace.upCalls++
        PreparedMigrationTrace.upInstance = this
        PreparedMigrationTrace.upFailure?.let { throw it }
        return versionDefinition("prepared_instance")
    }

    override fun down(): MigrationDefinition {
        PreparedMigrationTrace.downCalls++
        PreparedMigrationTrace.downInstance = this
        PreparedMigrationTrace.downFailure?.let { throw it }
        return versionDefinition("prepared_instance")
    }
}

private class ConstructorFailingMigration : Migration(MigrationItem) {
    init {
        throw PreparationFailures.constructorFailure
    }

    override fun up(): MigrationDefinition = error("Preparation must not run up()")

    override fun down(): MigrationDefinition = error("Preparation must not run down()")
}

private class FatalConstructorMigration : Migration(MigrationItem) {
    init {
        throw PreparationFailures.constructorFatal
    }

    override fun up(): MigrationDefinition = error("Preparation must not run up()")

    override fun down(): MigrationDefinition = error("Preparation must not run down()")
}

private object PreparationFailures {
    val constructorFailure = IllegalStateException("constructor failed")
    val constructorFatal = AssertionError("fatal constructor failure")
}

private object PreparedMigrationTrace {
    var constructorCalls = 0
    var constructedInstance: PreparedInstanceMigration? = null
    var upCalls = 0
    var upInstance: PreparedInstanceMigration? = null
    var upFailure: Throwable? = null
    var downCalls = 0
    var downInstance: PreparedInstanceMigration? = null
    var downFailure: Throwable? = null

    fun reset() {
        constructorCalls = 0
        constructedInstance = null
        upCalls = 0
        upInstance = null
        upFailure = null
        downCalls = 0
        downInstance = null
        downFailure = null
    }
}

private object VersionTag : MigrationVersionTag {
    override val value: String = "version_transaction_test"
}

private object VersionTagOne : MigrationVersionTag {
    override val value: String = "orchestration_v1"
}

private object VersionTagTwo : MigrationVersionTag {
    override val value: String = "orchestration_v2"
}

private object VersionTagThree : MigrationVersionTag {
    override val value: String = "orchestration_v3"
}

private object VersionTagFour : MigrationVersionTag {
    override val value: String = "orchestration_v4_empty"
}

private object VersionItemFirst : MigrationItemTag {
    override val value: String = "version_transaction_first"
}

private object VersionItemSecond : MigrationItemTag {
    override val value: String = "version_transaction_second"
}

private object VersionItemThird : MigrationItemTag {
    override val value: String = "version_transaction_third"
}

private object VersionItemFourth : MigrationItemTag {
    override val value: String = "version_transaction_fourth"
}

private object VersionItemFifth : MigrationItemTag {
    override val value: String = "version_transaction_fifth"
}

private object RetryFailureItem : MigrationItemTag {
    override val value: String = "retry_execution_failure_item"
}

private object UnsupportedDownItem : MigrationItemTag {
    override val value: String = "unsupported_down_item"
}

private object PreparedFirstItem : MigrationItemTag {
    override val value: String = "prepared_orchestration_first"
}

private object PreparedSecondItem : MigrationItemTag {
    override val value: String = "prepared_orchestration_second"
}

private object PreparedFailingItem : MigrationItemTag {
    override val value: String = "prepared_orchestration_failing"
}

private object PreparedVersionOne : MigrationVersionTag {
    override val value: String = "prepared_orchestration_v1"
}

private object PreparedVersionTwo : MigrationVersionTag {
    override val value: String = "prepared_orchestration_v2"
}

private object PreparedOrchestrationTrace {
    val events = mutableListOf<String>()
    val constructedInstances = mutableMapOf<String, Migration>()
    val executedInstances = mutableMapOf<String, Migration>()
    var failingConstructorEnabled = false
    val constructorFailure = IllegalStateException("late Version Migration constructor failed")

    fun constructed(name: String, migration: Migration) {
        events += "constructor:$name"
        constructedInstances[name] = migration
        if (name == "failing" && failingConstructorEnabled) throw constructorFailure
    }

    fun executed(direction: String, name: String, migration: Migration): MigrationDefinition {
        events += "$direction:$name"
        executedInstances[name] = migration
        return versionDefinition("prepared_orchestration_$name")
    }

    fun reset() {
        events.clear()
        constructedInstances.clear()
        executedInstances.clear()
        failingConstructorEnabled = false
    }
}

private class PreparedFirstMigration : Migration(PreparedFirstItem) {
    init {
        PreparedOrchestrationTrace.constructed("first", this)
    }

    override fun up(): MigrationDefinition = PreparedOrchestrationTrace.executed("up", "first", this)

    override fun down(): MigrationDefinition = PreparedOrchestrationTrace.executed("down", "first", this)
}

private class PreparedSecondMigration : Migration(PreparedSecondItem) {
    init {
        PreparedOrchestrationTrace.constructed("second", this)
    }

    override fun up(): MigrationDefinition = PreparedOrchestrationTrace.executed("up", "second", this)

    override fun down(): MigrationDefinition = PreparedOrchestrationTrace.executed("down", "second", this)
}

private class PreparedFailingMigration : Migration(PreparedFailingItem) {
    init {
        PreparedOrchestrationTrace.constructed("failing", this)
    }

    override fun up(): MigrationDefinition = error("UP must not execute after failed global preparation")

    override fun down(): MigrationDefinition = error("DOWN must not execute after failed global preparation")
}

private object VersionMigrationTrace {
    val events = mutableListOf<String>()

    fun reset() = events.clear()
}

private class VersionFirstMigration : Migration(VersionItemFirst) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:first"
        return versionDefinition("version_first")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:first"
        return versionDefinition("version_first")
    }
}

private class VersionSecondMigration : Migration(VersionItemSecond) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:second"
        return versionDefinition("version_second")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:second"
        return versionDefinition("version_second")
    }
}

private class VersionThirdMigration : Migration(VersionItemThird) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:third"
        return versionDefinition("version_third")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:third"
        return versionDefinition("version_third")
    }
}

private class VersionFourthMigration : Migration(VersionItemFourth) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:fourth"
        return versionDefinition("version_fourth")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:fourth"
        return versionDefinition("version_fourth")
    }
}

private class VersionFifthMigration : Migration(VersionItemFifth) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:fifth"
        return versionDefinition("version_fifth")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:fifth"
        return versionDefinition("version_fifth")
    }
}

private class RetryFailingMigration : Migration(RetryFailureItem) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "retry:up"
        throw SQLException("retry:up")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "retry:down"
        throw SQLException("retry:down")
    }
}

private class UnsupportedDownMigration : Migration(UnsupportedDownItem) {
    override fun up(): MigrationDefinition = versionDefinition("unsupported_down")

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:unsupported"
        throw UnsupportedOperationException("DOWN is not supported")
    }
}

private fun versionDefinition(tableName: String) = MigrationDefinition(
    Schema.createTable {
        table { tableName(tableName) }
        addColumn {
            name("id")
            dataType(SchemaDataType.IntType)
            notNull()
        }
    },
)
