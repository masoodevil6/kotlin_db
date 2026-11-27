package gog.my_project.data_base_migration.executor.integration

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseEventListener
import gog.my_project.data_base.core.events.DatabaseWatchSubscription
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_manager.facade.watch
import gog.my_project.data_base_migration.api.builder.migration
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.events.MigrationEvent
import gog.my_project.data_base_migration.core.events.MigrationEventType
import gog.my_project.data_base_migration.core.events.MigrationFailurePersistenceEvent
import gog.my_project.data_base_migration.core.events.MigrationFailurePersistenceOutcome
import gog.my_project.data_base_migration.core.events.MigrationFailurePersistenceWriteMode
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.facade.MigrationFacade
import gog.my_project.data_base_migration.facade.migration
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseProduct
import gog.my_project.data_base_operation.config.data_base.DatabaseServerInfo
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEventType
import gog.my_project.data_base_operation.executor.events.OperationLockEvent
import gog.my_project.data_base_operation.executor.events.OperationLockEventType
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.data_base_schema.SchemaOperation
import java.sql.DriverManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import java.util.concurrent.TimeUnit

@Tag("mysql-integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("migration-mariadb-dedicated-schema")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class MigrationMariaDbIntegrationTest {
    private lateinit var settings: MariaDbIntegrationSettings
    private lateinit var server: InspectedMariaDb
    private lateinit var manager: DatabaseManager
    private lateinit var handle: DatabaseHandleApi
    private lateinit var watchSubscription: DatabaseWatchSubscription
    private val operationConnectionCount = AtomicInteger()
    private val verificationConnectionIds = linkedSetOf<Long>()
    private val observedEvents = CopyOnWriteArrayList<DatabaseEvent>()
    private val primaryOperationId = AtomicReference<String?>()
    private val secondaryOperationId = AtomicReference<String?>()
    private val secondaryProbeEnabled = AtomicBoolean()
    private val secondaryReleaseTriggered = AtomicBoolean()
    private val observerFailure = AtomicReference<Throwable?>()
    private val secondaryAcquireFuture = AtomicReference<Future<ExecuteResult<Boolean>>?>()
    private lateinit var secondaryAcquireObserved: CountDownLatch
    private lateinit var secondaryAcquireExecutor: ExecutorService

    @BeforeAll
    fun validateDedicatedMariaDbTarget() {
        settings = MariaDbIntegrationSettings.fromEnvironment()
        server = settings.inspectMariaDb()
        manager = DatabaseManager {
            database(TestDatabase) {
                config = settings.databaseConfig(server.version)
            }
        }
        handle = manager[TestDatabase]
        watchSubscription = handle.watch(DatabaseEventListener(::observeDatabaseEvent))
        println(
            "REAL_MIGRATION_IT target=${server.product} ${server.version}; " +
                "schema=${settings.schema}; jdbcDriver=${server.driverName} ${server.driverVersion}",
        )
    }

    @AfterAll
    fun closeDatabaseManager() {
        if (::watchSubscription.isInitialized) watchSubscription.close()
        if (::manager.isInitialized) manager.shutdown()
    }

    @BeforeEach
    fun startWithCleanOwnedTables() {
        MigrationExecutionCounts.reset()
        verificationConnectionIds.clear()
        observedEvents.clear()
        primaryOperationId.set(null)
        secondaryOperationId.set(null)
        secondaryProbeEnabled.set(false)
        secondaryReleaseTriggered.set(false)
        observerFailure.set(null)
        secondaryAcquireFuture.set(null)
        secondaryAcquireObserved = CountDownLatch(1)
        secondaryAcquireExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "migration-it-secondary-lock").apply { isDaemon = true }
        }
        cleanOwnedTables()
        verificationConnectionIds.clear()
        operationConnectionCount.set(0)
    }

    @AfterEach
    fun leaveTheDedicatedSchemaClean() {
        cleanOwnedTables()
        if (::secondaryAcquireExecutor.isInitialized) {
            secondaryAcquireExecutor.shutdownNow()
            secondaryAcquireExecutor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun initCreatesAllMigrationTablesAndIsSafeToRepeat() {
        val configuration = migrationConfig { }

        assertMigrationSuccess(runMigration(configuration) { init() })
        val firstTables = migrationTables()
        assertEquals(EXPECTED_MIGRATION_TABLES, firstTables)
        val firstCounts = migrationRowCounts()
        assertEquals(MigrationRowCounts(0, 0, 0), firstCounts)

        assertMigrationSuccess(runMigration(configuration) { init() })
        assertEquals(firstTables, migrationTables())
        assertEquals(firstCounts, migrationRowCounts())

        reportPass("init", operationConnectionCount.get())
    }

    @Test
    fun successfulMigrationPersistsTagsVersionsAndHistoryInConfiguredOrder() {
        val configuration = orderedMigrationConfiguration()

        assertMigrationSuccess(runMigration(configuration) { init() })
        assertMigrationSuccess(runMigration(configuration) { migrate() })

        assertEquals(1, MigrationExecutionCounts.first.get())
        assertEquals(1, MigrationExecutionCounts.second.get())

        val tags = readRows(
            "SELECT tagName AS tag_name, tagType AS tag_type " +
                "FROM system_migration_tag ORDER BY tagName",
        ) { rows ->
            buildList {
                while (rows.next()) {
                    add(TagRow(requireNotNull(rows.getString("tag_name")), requireNotNull(rows.getInt("tag_type"))))
                }
            }
        }
        assertEquals(
            setOf(
                TagRow(IntegrationVersions.FIRST.value, MigrationTagType.VERSION.code),
                TagRow(IntegrationVersions.SECOND.value, MigrationTagType.VERSION.code),
                TagRow(IntegrationItems.CREATE_FIRST.value, MigrationTagType.ITEM.code),
                TagRow(IntegrationItems.CREATE_SECOND.value, MigrationTagType.ITEM.code),
            ),
            tags.toSet(),
        )

        val versions = readRows(
            "SELECT tag.tagName AS version_tag, version.status AS version_status " +
                "FROM system_migration_version version " +
                "JOIN system_migration_tag tag ON tag.id = version.tag_id " +
                "ORDER BY version.id",
        ) { rows ->
            buildList {
                while (rows.next()) {
                    add(VersionRow(
                        requireNotNull(rows.getString("version_tag")),
                        requireNotNull(rows.getInt("version_status")),
                    ))
                }
            }
        }
        assertEquals(
            listOf(
                VersionRow(IntegrationVersions.FIRST.value, STATUS_MIGRATE_COMPLETED),
                VersionRow(IntegrationVersions.SECOND.value, STATUS_MIGRATE_COMPLETED),
            ),
            versions,
        )

        val history = readRows(
            "SELECT version_tag.tagName AS version_tag, item_tag.tagName AS item_tag " +
                "FROM system_migration history " +
                "JOIN system_migration_tag item_tag ON item_tag.id = history.tag_id " +
                "JOIN system_migration_version version ON version.id = history.version_id " +
                "JOIN system_migration_tag version_tag ON version_tag.id = version.tag_id " +
                "ORDER BY version.id, history.id",
        ) { rows ->
            buildList {
                while (rows.next()) {
                    add(HistoryRow(
                        requireNotNull(rows.getString("version_tag")),
                        requireNotNull(rows.getString("item_tag")),
                    ))
                }
            }
        }
        assertEquals(
            listOf(
                HistoryRow(IntegrationVersions.FIRST.value, IntegrationItems.CREATE_FIRST.value),
                HistoryRow(IntegrationVersions.SECOND.value, IntegrationItems.CREATE_SECOND.value),
            ),
            history,
        )
        assertEquals(EXPECTED_USER_MIGRATION_TABLES, userMigrationTables())

        reportPass("successful-migration", operationConnectionCount.get())
    }

    @Test
    fun completedMigrationDoesNotRunItsCallbackAgain() {
        val configuration = orderedMigrationConfiguration()

        assertMigrationSuccess(runMigration(configuration) { init() })
        assertMigrationSuccess(runMigration(configuration) { migrate() })
        assertEquals(1, MigrationExecutionCounts.first.get())
        assertEquals(1, MigrationExecutionCounts.second.get())
        val firstHistory = historyCount()
        assertEquals(2L, firstHistory)

        assertMigrationSuccess(runMigration(configuration) { migrate() })
        assertEquals(1, MigrationExecutionCounts.first.get())
        assertEquals(1, MigrationExecutionCounts.second.get())
        assertEquals(firstHistory, historyCount())

        reportPass("idempotent-execution", operationConnectionCount.get())
    }

    @Test
    fun failedMigrationPersistsStatusAndCheckpointThenReleasesItsLockBeforeConnectionClose() {
        val configuration = failureMigrationConfiguration()
        assertMigrationSuccess(runMigration(configuration) { init() })

        observedEvents.clear()
        primaryOperationId.set(null)
        secondaryOperationId.set(null)
        secondaryReleaseTriggered.set(false)
        observerFailure.set(null)
        secondaryAcquireFuture.set(null)
        secondaryProbeEnabled.set(true)

        operationConnectionCount.incrementAndGet()
        val outerResult = handle.migration(configuration) { migrate() }
        val migrationFailure = when (outerResult) {
            is ExecuteResult.Failure -> outerResult.exception
            is ExecuteResult.Success -> when (val commandResult = outerResult.result) {
                null -> null
                is ExecuteResult.Failure -> commandResult.exception
                is ExecuteResult.Success -> null
            }
        }
        assertTrue(migrationFailure != null, "The controlled UP migration must fail")

        val secondFuture = requireNotNull(secondaryAcquireFuture.get()) {
            "The Watch listener did not start the independent lock acquisition"
        }
        val secondaryResult = secondFuture.get(30, TimeUnit.SECONDS)
        assertTrue(observerFailure.get() == null, "The Watch listener failed: ${observerFailure.get()}")
        assertTrue(
            secondaryResult is ExecuteResult.Success && secondaryResult.result == true,
            "The second manager-owned Operation must acquire and release the Migration lock",
        )
        val flowEvents = observedEvents.toList()
        val primaryId = requireNotNull(primaryOperationId.get()) { "MIGRATION_STARTED had no OperationId" }
        val secondaryId = requireNotNull(secondaryOperationId.get()) {
            "The second Connection did not acquire the Migration lock"
        }
        assertTrue(primaryId != secondaryId, "The independent Operation must have a distinct OperationId")
        assertTrue(flowEvents.all { it.operationId() != null }, "Every observed Operation event must carry its Root OperationId")
        assertEquals(setOf(primaryId, secondaryId), flowEvents.mapNotNull { it.operationId() }.toSet())

        val primaryEvents = flowEvents.filter { it.operationId() == primaryId }
        assertTrue(primaryEvents.isNotEmpty())
        assertTrue(primaryEvents.all { it.operationId() == primaryId })
        val primaryLockEvents = primaryEvents.filterIsInstance<OperationLockEvent>()
        assertTrue(primaryLockEvents.isNotEmpty())
        assertEquals(1, primaryLockEvents.map { it.lockAttemptId }.toSet().size)
        assertTrue(primaryLockEvents.all { it.namespace == MIGRATION_LOCK_NAMESPACE && it.name == MIGRATION_LOCK_NAME })
        val failedSql = primaryEvents.filterIsInstance<OperationDatabaseEvent>()
            .single { it.type == OperationDatabaseEventType.SQL_FAILED }
        assertTrue(failedSql.sql?.contains("migration_it_failure_duplicate") == true)
        assertTrue(failedSql.sql?.contains("duplicate_column") == true)

        val persistenceEvent = flowEvents.filterIsInstance<MigrationFailurePersistenceEvent>()
            .single { it.operationId == primaryId }
        assertEquals(MigrationFailurePersistenceWriteMode.STATUS_CHECKPOINT_CAS, persistenceEvent.writeMode)
        assertEquals(MigrationFailurePersistenceOutcome.APPLIED, persistenceEvent.statusOutcome)
        assertEquals(MigrationFailurePersistenceOutcome.APPLIED, persistenceEvent.checkpointOutcome)

        val primaryOpened = primaryEvents.filterIsInstance<OperationDatabaseEvent>()
            .single { it.type == OperationDatabaseEventType.CONNECTION_OPENED }
        val primaryAcquireStarted = primaryLockEvents.single { it.type == OperationLockEventType.LOCK_ACQUISITION_STARTED }
        val primaryAcquired = primaryLockEvents.single { it.type == OperationLockEventType.LOCK_ACQUIRED }
        val primaryReleaseStarted = primaryLockEvents.single { it.type == OperationLockEventType.LOCK_RELEASE_STARTED }
        val primaryReleased = primaryLockEvents.single { it.type == OperationLockEventType.LOCK_RELEASED }
        val migrationFailed = primaryEvents.filterIsInstance<MigrationEvent>()
            .single { it.type == MigrationEventType.MIGRATION_FAILED }
        val primaryClosed = primaryEvents.filterIsInstance<OperationDatabaseEvent>()
            .single { it.type == OperationDatabaseEventType.CONNECTION_CLOSED }
        val secondaryAcquired = flowEvents.filterIsInstance<OperationLockEvent>()
            .single { it.operationId == secondaryId && it.type == OperationLockEventType.LOCK_ACQUIRED }
        val secondaryLockEvents = flowEvents.filterIsInstance<OperationLockEvent>().filter { it.operationId == secondaryId }
        assertEquals(1, secondaryLockEvents.map { it.lockAttemptId }.toSet().size)
        assertTrue(secondaryLockEvents.all {
            it.namespace == MIGRATION_LOCK_NAMESPACE && it.name == MIGRATION_LOCK_NAME
        })
        assertTrue(secondaryLockEvents.first().lockAttemptId != primaryLockEvents.first().lockAttemptId)

        assertEquals(primaryId, primaryOpened.operationId)
        assertEquals(primaryId, migrationFailed.operationId)
        assertEquals(primaryLockEvents.first().lockAttemptId, primaryAcquireStarted.lockAttemptId)
        assertEquals(primaryAcquireStarted.lockAttemptId, primaryAcquired.lockAttemptId)
        assertEquals(primaryAcquired.lockAttemptId, primaryReleaseStarted.lockAttemptId)
        assertEquals(primaryReleaseStarted.lockAttemptId, primaryReleased.lockAttemptId)
        assertEquals(MIGRATION_LOCK_NAMESPACE, secondaryAcquired.namespace)
        assertEquals(MIGRATION_LOCK_NAME, secondaryAcquired.name)
        assertTrue(
            flowEvents.indexOf(persistenceEvent) < flowEvents.indexOf(primaryReleaseStarted),
            "Failure Status and Checkpoint outcome must be published before lock release starts",
        )
        assertTrue(flowEvents.indexOf(primaryReleaseStarted) < flowEvents.indexOf(primaryReleased))
        assertTrue(
            flowEvents.indexOf(primaryReleased) < flowEvents.indexOf(secondaryAcquired),
            "The second Connection may acquire only after the owner released the lock",
        )
        assertTrue(
            flowEvents.indexOf(secondaryAcquired) < flowEvents.indexOf(migrationFailed),
            "The independent Connection must acquire while the owner Operation is still completing",
        )
        assertTrue(flowEvents.indexOf(primaryReleased) < flowEvents.indexOf(migrationFailed))
        assertTrue(
            flowEvents.indexOf(migrationFailed) < flowEvents.indexOf(primaryClosed),
            "Migration failure reporting must precede the owner's Connection close event",
        )
        assertTrue(
            flowEvents.indexOf(secondaryAcquired) < flowEvents.indexOf(primaryClosed),
            "The second Connection must acquire before the owner Connection closes",
        )
        val failureState = readFailureVersionState()
        assertTrue(verificationConnectionIds.size >= 2, "Independent manager Operations must use distinct Connections")
        assertEquals(1, MigrationExecutionCounts.failureFirst.get())
        assertEquals(1, MigrationExecutionCounts.failureSecond.get())
        assertEquals(1L, failureState.versionCount, "The failure must not change an unrelated Version")
        assertEquals(MigrationVersionState.STATUS_MIGRATE_FAILED_UP, failureState.status)
        assertEquals(1L, failureState.historyCount, "Only the first completed Migration should be in History")
        assertTrue(
            failureState.message?.contains("Duplicate column name", ignoreCase = true) == true,
            "The persisted diagnostic must identify the controlled duplicate-column SQL failure",
        )
        val checkpointJson = requireNotNull(failureState.retryCheckpoint) {
            "The failed UP Version must persist its Retry Checkpoint"
        }
        val checkpoint = MigrationRetryCheckpointCodec.decode(checkpointJson)
        assertEquals(MigrationRetryPhase.EXECUTION_IN_FLIGHT, checkpoint.phase)
        assertEquals(IntegrationItems.FAILURE_SECOND.value, checkpoint.cursorMigrationId)
        assertEquals(MigrationVersionState.STATUS_MIGRATE_STARTED, checkpoint.originalStatus)
        assertTrue(checkpoint.revision > 0)
        assertTrue(
            checkpoint.originalMessage?.contains("Duplicate column name", ignoreCase = true) == true,
            "The Checkpoint must retain the original duplicate-column diagnostic",
        )
        assertEquals(failureState.versionId, persistenceEvent.versionId)

        val semanticOrder = flowEvents.mapIndexedNotNull { index, event ->
            val summary = when (event) {
                is OperationLockEvent ->
                    "${event.type}(operationId=${event.operationId}, lockAttemptId=${event.lockAttemptId})"
                is MigrationFailurePersistenceEvent ->
                    "MigrationFailurePersistenceEvent(operationId=${event.operationId}, " +
                        "status=${event.statusOutcome}, checkpoint=${event.checkpointOutcome})"
                is MigrationEvent -> if (event.type == MigrationEventType.MIGRATION_STARTED ||
                    event.type == MigrationEventType.MIGRATION_FAILED
                ) {
                    "${event.type}(operationId=${event.operationId})"
                } else {
                    null
                }
                is OperationDatabaseEvent -> if (
                    event.type == OperationDatabaseEventType.CONNECTION_OPENED ||
                    event.type == OperationDatabaseEventType.CONNECTION_CLOSED
                ) {
                    "${event.type}(operationId=${event.operationId})"
                } else {
                    null
                }
                else -> null
            }
            summary?.let { "$index:$it" }
        }
        println("REAL_MIGRATION_IT EVENT_ORDER " + semanticOrder.joinToString(" -> "))

        reportPass("failure-status-checkpoint-lock-lifecycle", operationConnectionCount.get())
    }

    private fun orderedMigrationConfiguration(): MigrationConfiguration = migrationConfig {
        migrationVersion(IntegrationVersions.FIRST) {
            migration<FirstIntegrationMigration>()
        }
        migrationVersion(IntegrationVersions.SECOND) {
            migration<SecondIntegrationMigration>()
        }
    }

    private fun failureMigrationConfiguration(): MigrationConfiguration = migrationConfig {
        migrationVersion(IntegrationVersions.FAILURE) {
            migration<FirstFailureIntegrationMigration>()
            migration<SecondFailureIntegrationMigration>()
        }
    }

    private fun observeDatabaseEvent(event: DatabaseEvent) {
        observedEvents += event
        if (event is MigrationEvent && event.type == MigrationEventType.MIGRATION_STARTED) {
            primaryOperationId.set(event.operationId)
        }

        val primaryId = primaryOperationId.get()
        if (secondaryProbeEnabled.get() &&
            event is OperationLockEvent &&
            event.type == OperationLockEventType.LOCK_ACQUIRED &&
            event.operationId != null &&
            event.operationId != primaryId &&
            event.namespace == MIGRATION_LOCK_NAMESPACE &&
            event.name == MIGRATION_LOCK_NAME
        ) {
            secondaryOperationId.compareAndSet(null, event.operationId)
            secondaryAcquireObserved.countDown()
        }

        if (secondaryProbeEnabled.get() &&
            event is OperationLockEvent &&
            event.type == OperationLockEventType.LOCK_RELEASED &&
            event.operationId == primaryId &&
            event.namespace == MIGRATION_LOCK_NAMESPACE &&
            event.name == MIGRATION_LOCK_NAME &&
            secondaryReleaseTriggered.compareAndSet(false, true)
        ) {
            try {
                val future = secondaryAcquireExecutor.submit<ExecuteResult<Boolean>> {
                    acquireMigrationLockOnSecondConnection()
                }
                secondaryAcquireFuture.set(future)
                if (!secondaryAcquireObserved.await(20, TimeUnit.SECONDS)) {
                    observerFailure.compareAndSet(
                        null,
                        AssertionError("The second Connection did not acquire the released Migration lock in time"),
                    )
                }
            } catch (failure: Throwable) {
                observerFailure.compareAndSet(null, failure)
            }
        }
    }

    private fun acquireMigrationLockOnSecondConnection(): ExecuteResult<Boolean> {
        operationConnectionCount.incrementAndGet()
        return handle.withOperationScope { scope ->
            when (val target = verifyScopeTarget(scope)) {
                is ExecuteResult.Failure -> target
                is ExecuteResult.Success -> if (target.result == true) {
                    scope.withMigrationFailureStatusCapability(0) {
                        ExecuteResult.Success(true)
                    }
                } else {
                    scope.failed(IllegalStateException("Second Operation selected an unexpected schema"))
                }
            }
        }
    }

    private fun runMigration(
        configuration: MigrationConfiguration,
        block: MigrationFacade.() -> ExecuteResult<Boolean>,
    ): ExecuteResult<Boolean> {
        operationConnectionCount.incrementAndGet()
        return when (val outer = handle.migration(configuration, block)) {
            is ExecuteResult.Failure -> throw AssertionError("Manager-owned Migration Operation failed", outer.exception)
            is ExecuteResult.Success -> requireNotNull(outer.result) {
                "Manager-owned Migration API returned no command result"
            }
        }
    }

    private fun assertMigrationSuccess(result: ExecuteResult<Boolean>) {
        when (result) {
            is ExecuteResult.Failure -> throw AssertionError("Migration command failed", result.exception)
            is ExecuteResult.Success -> assertEquals(true, result.result)
        }
    }

    private fun cleanOwnedTables() {
        managedScope { scope ->
            CLEANUP_SQL.forEach { sql ->
                when (val result = scope.executeDdl(sql)) {
                    is ExecuteResult.Failure -> return@managedScope result
                    is ExecuteResult.Success -> if (result.result != true) {
                        return@managedScope scope.failed(IllegalStateException("Test schema cleanup DDL did not complete"))
                    }
                }
            }
            ExecuteResult.Success(true)
        }.also { result ->
            if (result != true) throw AssertionError("Test schema cleanup did not complete")
        }
    }

    private fun migrationTables(): Set<String> = readRows(
        "SELECT TABLE_NAME AS table_name FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN " +
            "('system_migration_tag', 'system_migration_version', 'system_migration') " +
            "ORDER BY TABLE_NAME",
    ) { rows ->
        buildSet {
            while (rows.next()) add(requireNotNull(rows.getString("table_name")))
        }
    }

    private fun userMigrationTables(): Set<String> = readRows(
        "SELECT TABLE_NAME AS table_name FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN " +
            "('migration_it_first', 'migration_it_second') ORDER BY TABLE_NAME",
    ) { rows ->
        buildSet {
            while (rows.next()) add(requireNotNull(rows.getString("table_name")))
        }
    }

    private fun migrationRowCounts(): MigrationRowCounts = readRows(
        "SELECT (SELECT COUNT(*) FROM system_migration_tag) AS tag_count, " +
            "(SELECT COUNT(*) FROM system_migration_version) AS version_count, " +
            "(SELECT COUNT(*) FROM system_migration) AS history_count",
    ) { rows ->
        check(rows.next()) { "Migration table counts query returned no row" }
        MigrationRowCounts(
            requireNotNull(rows.getLong("tag_count")),
            requireNotNull(rows.getLong("version_count")),
            requireNotNull(rows.getLong("history_count")),
        )
    }

    private fun historyCount(): Long = readRows(
        "SELECT COUNT(*) AS history_count FROM system_migration",
    ) { rows ->
        check(rows.next()) { "Migration history count query returned no row" }
        requireNotNull(rows.getLong("history_count"))
    }

    private fun readFailureVersionState(): FailureVersionRow = readRows(
        "SELECT version.id AS version_id, version.status AS version_status, " +
            "version.message AS version_message, version.retry_checkpoint AS retry_checkpoint, " +
            "(SELECT COUNT(*) FROM system_migration history WHERE history.version_id = version.id) AS history_count, " +
            "(SELECT COUNT(*) FROM system_migration_version) AS version_count " +
            "FROM system_migration_version version " +
            "JOIN system_migration_tag version_tag ON version_tag.id = version.tag_id " +
            "WHERE version_tag.tagName = '${IntegrationVersions.FAILURE.value}'",
    ) { rows ->
        check(rows.next()) { "Failed Migration Version was not persisted" }
        val state = FailureVersionRow(
            versionId = requireNotNull(rows.getInt("version_id")),
            status = requireNotNull(rows.getInt("version_status")),
            message = rows.getString("version_message"),
            retryCheckpoint = rows.getString("retry_checkpoint"),
            historyCount = requireNotNull(rows.getLong("history_count")),
            versionCount = requireNotNull(rows.getLong("version_count")),
        )
        check(!rows.next()) { "More than one failure row was persisted for the test Version" }
        state
    }

    private fun <T> readRows(sql: String, read: (OperationQueryRows) -> T): T =
        managedScope { scope -> scope.executeRead(sql, read = read) }

    private fun <T> managedScope(block: (OperationExecutionScope) -> ExecuteResult<T>): T {
        operationConnectionCount.incrementAndGet()
        val result = handle.withOperationScope { scope ->
            when (val target = verifyScopeTarget(scope)) {
                is ExecuteResult.Failure -> target
                is ExecuteResult.Success -> if (target.result == true) block(scope) else {
                    scope.failed(IllegalStateException("Operation Scope selected an unexpected schema"))
                }
            }
        }
        return when (result) {
            is ExecuteResult.Failure -> throw AssertionError("Manager-owned Operation Scope failed", result.exception)
            is ExecuteResult.Success -> requireNotNull(result.result) { "Manager-owned Operation Scope returned null" }
        }
    }

    private fun verifyScopeTarget(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        when (
            val selectedSchema = scope.executeRead(
                "SELECT DATABASE() AS selected_schema, CONNECTION_ID() AS connection_id",
            ) { rows ->
                check(rows.next()) { "Operation target verification returned no row" }
                val schema = requireNotNull(rows.getString("selected_schema"))
                val connectionId = requireNotNull(rows.getLong("connection_id"))
                check(!rows.next()) { "Operation target verification returned multiple rows" }
                schema to connectionId
            }
        ) {
            is ExecuteResult.Failure -> selectedSchema
            is ExecuteResult.Success -> {
                val (schema, connectionId) = requireNotNull(selectedSchema.result)
                verificationConnectionIds += connectionId
                if (schema != settings.schema || scope.databaseIdentity != settings.databaseIdentity(server.version)) {
                    scope.failed(IllegalStateException("Operation Scope did not match the allowlisted Migration test target"))
                } else {
                    ExecuteResult.Success(true)
                }
            }
        }

    private fun reportPass(test: String, operationConnections: Int) {
        println(
            "REAL_MIGRATION_IT PASS test=" + test + "; server=" + server.product + " " + server.version +
                "; jdbcDriver=" + server.driverName + " " + server.driverVersion +
                "; schema=" + settings.schema + "; independentOperationConnections=" + operationConnections +
                "; verifiedConnectionIds=" + verificationConnectionIds.size,
        )
    }

    private data class MigrationRowCounts(
        val tags: Long,
        val versions: Long,
        val history: Long,
    )

    private data class TagRow(val name: String, val type: Int)
    private data class VersionRow(val tag: String, val status: Int)
    private data class HistoryRow(val version: String, val item: String)

    private data class FailureVersionRow(
        val versionId: Int,
        val status: Int,
        val message: String?,
        val retryCheckpoint: String?,
        val historyCount: Long,
        val versionCount: Long,
    )

    private data class InspectedMariaDb(
        val product: DatabaseProduct,
        val version: String,
        val driverName: String,
        val driverVersion: String,
    )

    private data class MariaDbIntegrationSettings(
        val host: String,
        val port: Int,
        val schema: String,
        val allowedSchemas: Set<String>,
        val username: String,
        val password: String,
    ) {
        private val domain: String get() = "jdbc:mysql://" + host

        fun databaseConfig(serverVersion: String): DatabaseConfig = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = domain,
                dbPort = port,
                dbName = schema,
                dbUserName = username,
                dbPassword = password,
            ),
            platform = DatabasePlatformConfig(
                dialect = DatabaseDialect.MARIA_DB,
                targetDatabaseVersion = DatabaseServerInfo.parse(serverVersion).version,
            ),
        )

        fun databaseIdentity(serverVersion: String): DatabaseIdentity = requireNotNull(
            DatabaseIdentity.from(databaseConfig(serverVersion)),
        ) { "Configured Migration test endpoint did not produce a canonical DatabaseIdentity" }

        fun inspectMariaDb(): InspectedMariaDb = DriverManager.getConnection(
            jdbcUrl(),
            username,
            password,
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT VERSION() AS server_version, @@version_comment AS product_comment, " +
                        "DATABASE() AS selected_schema, CURRENT_USER() AS effective_user",
                ).use { rows ->
                    check(rows.next()) { "MariaDB did not return server metadata" }
                    val version = requireNotNull(rows.getString("server_version"))
                    val productComment = requireNotNull(rows.getString("product_comment"))
                    val selectedSchema = requireNotNull(rows.getString("selected_schema"))
                    val effectiveUser = requireNotNull(rows.getString("effective_user")).substringBefore('@')
                    check(version.contains("MariaDB", ignoreCase = true) &&
                        productComment.contains("MariaDB", ignoreCase = true)
                    ) { "Migration integration requires MariaDB; received a different server product" }
                    check(selectedSchema == schema && selectedSchema in allowedSchemas) {
                        "Connected schema did not match the explicitly allowlisted Migration test schema"
                    }
                    check(!effectiveUser.equals("root", ignoreCase = true)) {
                        "Migration integration requires a dedicated non-root database account"
                    }
                    check(!rows.next()) { "MariaDB returned unexpected extra metadata rows" }
                    val parsedServer = DatabaseServerInfo.parse(version)
                    check(parsedServer.product == DatabaseProduct.MARIA_DB) {
                        "Server metadata parser did not identify MariaDB"
                    }
                    InspectedMariaDb(
                        product = parsedServer.product,
                        version = version,
                        driverName = connection.metaData.driverName,
                        driverVersion = connection.metaData.driverVersion,
                    )
                }
            }
        }

        private fun jdbcUrl(): String = domain + ":" + port + "/" + schema + "?serverTimezone=UTC"

        companion object {
            private const val DOMAIN = "KOTLIN_DB_MIGRATION_IT_DOMAIN"
            private const val PORT = "KOTLIN_DB_MIGRATION_IT_PORT"
            private const val SCHEMA = "KOTLIN_DB_MIGRATION_IT_SCHEMA"
            private const val ALLOWED_SCHEMAS = "KOTLIN_DB_MIGRATION_IT_ALLOWED_SCHEMAS"
            private const val USER = "KOTLIN_DB_MIGRATION_IT_USER"
            private const val PASSWORD = "KOTLIN_DB_MIGRATION_IT_PASSWORD"
            private val TEST_SCHEMA_PATTERN = Regex("kotlin_db_migration_it_[a-z0-9_]{1,48}")
            private val FORBIDDEN_SCHEMAS = setOf(
                "main_example",
                "analytics_example",
                "main",
                "production",
                "information_schema",
                "mysql",
                "performance_schema",
                "sys",
            )

            fun fromEnvironment(): MariaDbIntegrationSettings {
                val configuredDomain = requiredEnvironment(DOMAIN)
                val host = Regex("^jdbc:mysql://(localhost|127\\.0\\.0\\.1)(?:/)?$")
                    .matchEntire(configuredDomain)?.groupValues?.get(1)
                    ?: error(DOMAIN + " must use localhost or 127.0.0.1 without a database path")
                val port = requiredEnvironment(PORT).toIntOrNull()
                    ?: error(PORT + " must be an integer")
                require(port in 1..65535) { PORT + " must be between 1 and 65535" }
                val schema = requiredEnvironment(SCHEMA)
                require(schema.matches(TEST_SCHEMA_PATTERN) && schema !in FORBIDDEN_SCHEMAS) {
                    SCHEMA + " must use the dedicated kotlin_db_migration_it_ test-schema prefix"
                }
                val allowedSchemas = requiredEnvironment(ALLOWED_SCHEMAS)
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet()
                require(allowedSchemas.isNotEmpty()) { ALLOWED_SCHEMAS + " must list the disposable test schema explicitly" }
                require(allowedSchemas.all { it.matches(TEST_SCHEMA_PATTERN) && it !in FORBIDDEN_SCHEMAS }) {
                    ALLOWED_SCHEMAS + " may contain only dedicated Migration test schema names"
                }
                require(schema in allowedSchemas) {
                    "Configured Migration schema is not present in the explicit test allowlist"
                }
                val username = requiredEnvironment(USER)
                require(username.isNotBlank() && !username.equals("root", ignoreCase = true)) {
                    USER + " must name a dedicated non-root database account"
                }
                val password = requiredEnvironment(PASSWORD)
                require(password.isNotEmpty()) { PASSWORD + " must not be empty" }
                return MariaDbIntegrationSettings(host, port, schema, allowedSchemas, username, password)
            }

            private fun requiredEnvironment(name: String): String =
                requireNotNull(System.getenv(name)?.takeIf(String::isNotEmpty)) {
                    "Missing required dedicated Migration MariaDB integration setting: " + name
                }
        }
    }

    private companion object {
        val EXPECTED_MIGRATION_TABLES = setOf(
            "system_migration_tag",
            "system_migration_version",
            "system_migration",
        )
        val EXPECTED_USER_MIGRATION_TABLES = setOf("migration_it_first", "migration_it_second")
        val CLEANUP_SQL = listOf(
            "DROP TABLE IF EXISTS system_migration",
            "DROP TABLE IF EXISTS system_migration_version",
            "DROP TABLE IF EXISTS system_migration_tag",
            "DROP TABLE IF EXISTS migration_it_failure_duplicate",
            "DROP TABLE IF EXISTS migration_it_failure_applied",
            "DROP TABLE IF EXISTS migration_it_second",
            "DROP TABLE IF EXISTS migration_it_first",
        )
        const val STATUS_MIGRATE_COMPLETED = 103
        const val MIGRATION_LOCK_NAMESPACE = "textqb:migration"
        const val MIGRATION_LOCK_NAME = "system"
    }
}

private object TestDatabase : DatabaseKey

private enum class IntegrationVersions(override val value: String) : MigrationVersionTag {
    FIRST("migration_it_v1"),
    SECOND("migration_it_v2"),
    FAILURE("migration_it_failure_v1"),
}

private enum class IntegrationItems(override val value: String) : MigrationItemTag {
    CREATE_FIRST("migration_it_create_first"),
    CREATE_SECOND("migration_it_create_second"),
    FAILURE_FIRST("migration_it_failure_first"),
    FAILURE_SECOND("migration_it_failure_second"),
}

private object MigrationExecutionCounts {
    val first = AtomicInteger()
    val second = AtomicInteger()
    val failureFirst = AtomicInteger()
    val failureSecond = AtomicInteger()

    fun reset() {
        first.set(0)
        second.set(0)
        failureFirst.set(0)
        failureSecond.set(0)
    }
}

private class FirstIntegrationMigration : Migration(IntegrationItems.CREATE_FIRST) {
    override fun up(): MigrationDefinition {
        MigrationExecutionCounts.first.incrementAndGet()
        return tableMigration("migration_it_first")
    }

    override fun down(): MigrationDefinition = dropTableMigration("migration_it_first")
}

private class SecondIntegrationMigration : Migration(IntegrationItems.CREATE_SECOND) {
    override fun up(): MigrationDefinition {
        MigrationExecutionCounts.second.incrementAndGet()
        return tableMigration("migration_it_second")
    }

    override fun down(): MigrationDefinition = dropTableMigration("migration_it_second")
}

private class FirstFailureIntegrationMigration : Migration(IntegrationItems.FAILURE_FIRST) {
    override fun up(): MigrationDefinition {
        MigrationExecutionCounts.failureFirst.incrementAndGet()
        return tableMigration("migration_it_failure_applied")
    }

    override fun down(): MigrationDefinition = dropTableMigration("migration_it_failure_applied")
}

private class SecondFailureIntegrationMigration : Migration(IntegrationItems.FAILURE_SECOND) {
    override fun up(): MigrationDefinition {
        MigrationExecutionCounts.failureSecond.incrementAndGet()
        return duplicateColumnTableMigration("migration_it_failure_duplicate")
    }

    override fun down(): MigrationDefinition = dropTableMigration("migration_it_failure_duplicate")
}

private fun tableMigration(name: String): MigrationDefinition = migration {
    operation(Schema.createTable {
        table { tableName(name) }
        addColumn {
            name("id")
            dataType(SchemaDataType.IntType)
            notNull()
            autoIncrement()
            primaryKey()
        }
    })
}

private fun dropTableMigration(name: String): MigrationDefinition = migration {
    operation(Schema.dropTable { tableName(name) })
}

private fun duplicateColumnTableMigration(name: String): MigrationDefinition = migration {
    operation(Schema.createTable {
        table { tableName(name) }
        addColumn {
            name("duplicate_column")
            dataType(SchemaDataType.IntType)
        }
        addColumn {
            name("duplicate_column")
            dataType(SchemaDataType.IntType)
        }
    })
}

private fun DatabaseEvent.operationId(): String? = when (this) {
    is OperationDatabaseEvent -> operationId
    is OperationLockEvent -> operationId
    is MigrationEvent -> operationId
    is MigrationFailurePersistenceEvent -> operationId
    else -> null
}
