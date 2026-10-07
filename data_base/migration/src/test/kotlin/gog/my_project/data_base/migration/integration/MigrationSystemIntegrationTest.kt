package gog.my_project.data_base.migration.integration

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.builder.addColumn
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.builder.dropForeignKey as buildDropForeignKey
import gog.my_project.data_base.migration.builder.dropTable
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.builder.migrationConfig
import gog.my_project.data_base.migration.executor.manager.MigrationExecutor
import gog.my_project.data_base.migration.executor.manager.MigrationMigrator
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.VarcharType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.manager.QueryBuilderExecutor
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MigrationSystemIntegrationTest {
    @Test
    fun refreshOptionDefaultsToFalseAndRejectsValuesOtherThanTrueOrFalse() {
        assertFalse(parseRefreshDatabaseOption(null))
        assertTrue(parseRefreshDatabaseOption("true"))
        assertFalse(parseRefreshDatabaseOption("false"))
        assertFailsWith<IllegalStateException> { parseRefreshDatabaseOption("") }
        assertFailsWith<IllegalStateException> { parseRefreshDatabaseOption("TRUE") }
        assertFailsWith<IllegalStateException> { parseRefreshDatabaseOption(" true ") }
    }

    @Test
    fun refreshFailureStopsBeforeLifecycleAndRestoresPreviousConfig() {
        val previousConfig = DefaultDatabaseConfig.config
        val testConfig = previousConfig.copy(dbName = "refresh_failure_probe")
        val restoreTestConfig = installTestConfig(testConfig)
        var migrationMigratorStarted = false

        try {
            assertFailsWith<IllegalStateException> {
                refreshBeforeMigrationLifecycle(enabled = true, restoreTestConfig = restoreTestConfig) {
                    error("simulated refresh failure")
                }
                migrationMigratorStarted = true
            }
        } finally {
            restoreTestConfig()
        }

        assertFalse(migrationMigratorStarted)
        assertEquals(previousConfig, DefaultDatabaseConfig.config)
    }

    @Test
    fun runsTheMigrationSubsystemLifecycleAgainstMySql() {
        val runtime = TestRuntimeRecorder.create(
            TestRuntimeRecorder.PROJECT_PATH,
            TestRuntimeRecorder.SYSTEM_TEST_ID,
            listOf("prepare-config", "open-connection", "refresh-collision", "successful-migrations", "rerun", "failure-resume", "operation-validation", "final-state", "restore-resources"),
            parentIds = mapOf(
                "prepare-config" to "system-setup",
                "open-connection" to "system-setup",
                "refresh-collision" to "system-setup",
                "successful-migrations" to "system-migrations",
                "rerun" to "system-migrations",
                "failure-resume" to "system-failure-cases",
                "operation-validation" to "system-failure-cases",
                "final-state" to "system-finalization",
                "restore-resources" to "system-finalization",
            ),
        )
        runtime?.begin()
        runtime?.startStep("prepare-config")
        try {
        val refreshDatabase = parseRefreshDatabaseOption(System.getProperty(DB_REFRESH_PROPERTY))
        val dbDomain = requiredNonBlankProperty(DB_DOMAIN_PROPERTY)
        val port = requiredNonBlankProperty(DB_PORT_PROPERTY).toIntOrNull()
            ?: error("Gradle property '$DB_PORT_PROPERTY' must be an integer")
        require(port in 1..65535) { "Gradle property '$DB_PORT_PROPERTY' must be between 1 and 65535" }
        require(dbDomain.startsWith("jdbc:mysql://")) {
            "Gradle property '$DB_DOMAIN_PROPERTY' must use the MySQL JDBC URL prefix"
        }
        val database = requiredNonBlankProperty(DB_NAME_PROPERTY)
        val username = requiredNonBlankProperty(DB_USERNAME_PROPERTY)
        val password = System.getProperty(DB_PASSWORD_PROPERTY)
            ?: error("Missing Gradle property '$DB_PASSWORD_PROPERTY'; set it explicitly, even when empty")
        val config = DatabaseConfig(
            dbDomain = dbDomain,
            dbPort = port,
            dbName = database,
            dbUserName = username,
            dbPassword = password,
            dbPoolSize = 10,
            dialect = DialectQuery.MY_SQL,
        )
        val previousConfig = DefaultDatabaseConfig.config
        var connection: Connection? = null
        var restoreTestConfig: (() -> Unit)? = null
        var testFailure: Throwable? = null
        runtime?.succeedStep("prepare-config")
        try {
            runtime?.startStep("open-connection")
            restoreTestConfig = installTestConfig(config)
            val testConnection = DriverManager.getConnection(config.getDbUrl(), config.dbUserName, config.dbPassword)
            connection = testConnection
            assertSelectedCatalog(testConnection, config.dbName)
            runtime?.succeedStep("open-connection")
            runtime?.startStep("refresh-collision")

            refreshBeforeMigrationLifecycle(
                enabled = refreshDatabase,
                restoreTestConfig = requireNotNull(restoreTestConfig),
                refresh = { refreshAllTables(testConnection) },
            )

            if (refreshDatabase) {
                assertTrue(tableNames(testConnection).isEmpty(), "Database refresh must leave no user tables")
            } else {
                val collisions = existingOwnedObjects(testConnection)
                if (collisions.isNotEmpty()) {
                    val tablesBefore = tableNames(testConnection)
                    val collision = assertFailsWith<AssertionError> { assertNoOwnedTableCollisions(testConnection) }
                    assertTrue(collision.message.orEmpty().contains("test table", ignoreCase = true))
                    assertEquals(tablesBefore, tableNames(testConnection), "Collision handling changed database objects")
                    runtime?.succeedStep("refresh-collision", mapOf("collisionDetected" to true, "migrationStarted" to false))
                    runtime?.markRemainingNotExecuted(3)
                    return
                }
            }

            assertNoOwnedTableCollisions(testConnection)
            runtime?.succeedStep("refresh-collision", mapOf("collisionDetected" to false, "refreshed" to refreshDatabase))

            runtime?.startStep("successful-migrations")
            val successfulConfiguration = migrationConfig {
                migration<CreateTestUsers>()
                migrationGroup("test_users") {
                    migration<AddTestUsersEmail>()
                }
                migration<CreateTestPosts>()
                migration<CreateFailureBlocker>()
            }
            val successfulMigrator = MigrationMigrator(successfulConfiguration)

            val successfulResult = successfulMigrator.migrateAndCapture()
            val expectedSuccessfulIds = listOf(
                migrationTags.create_test_users,
                migrationTags.add_test_users_email,
                migrationTags.create_test_posts,
                migrationTags.create_failure_blocker,
            )
            val firstHistory = migrationHistory()
            runtime?.recordMigrations(expectedSuccessfulIds, firstHistory.map { it.migration to it.batch }, executionSucceeded = successfulResult is ExecuteResult.Success)
            assertIs<ExecuteResult.Success<Unit>>(successfulResult)

            assertEquals(expectedSuccessfulIds, firstHistory.map(HistoryRow::migration))
            assertEquals(listOf(1, 1, 1, 1), firstHistory.map(HistoryRow::batch))
            assertTrue(TEST_USERS_TABLE in tableNames(testConnection))
            assertTrue(TEST_POSTS_TABLE in tableNames(testConnection))
            assertTrue("email" in columnNames(testConnection, TEST_USERS_TABLE))
            runtime?.succeedStep("successful-migrations", mapOf("migrationCount" to firstHistory.size))

            runtime?.startStep("rerun")
            val firstSchema = ownedFixtureTables.associateWith { table ->
                if (table in tableNames(testConnection)) columnNames(testConnection, table) else emptySet()
            }
            assertIs<ExecuteResult.Success<Unit>>(successfulMigrator.migrateAndCapture())
            assertEquals(firstHistory, migrationHistory())
            assertEquals(
                firstSchema,
                ownedFixtureTables.associateWith { table ->
                    if (table in tableNames(testConnection)) columnNames(testConnection, table) else emptySet()
                },
            )
            runtime?.succeedStep("rerun", mapOf("historyUnchanged" to true))

            runtime?.startStep("failure-resume")
            val failureConfiguration = migrationConfig {
                migration<CreateFailureBlocker>()
                migration<FailureCreateUsers>()
                migrationGroup("failure_users") {
                    migration<FailureAddEmail>()
                }
                migration<BrokenMigration>()
                migration<MigrationAfterFailure>()
            }
            val failureMigrator = MigrationMigrator(failureConfiguration)

            val firstFailure = assertIs<ExecuteResult.Failure>(failureMigrator.migrateAndCapture())
            assertTrue(firstFailure.exception.message.orEmpty().contains(FAILURE_BLOCKER_TABLE))
            assertTrue(FAILURE_USERS_TABLE in tableNames(testConnection))
            assertTrue("email" in columnNames(testConnection, FAILURE_USERS_TABLE))
            assertFalse(AFTER_FAILURE_TABLE in tableNames(testConnection))

            val historyAfterFailure = migrationHistory()
            runtime?.recordMigrations(
                listOf(migrationTags.create_failure_blocker, migrationTags.failure_create_users, migrationTags.failure_add_email, migrationTags.broken_migration, migrationTags.migration_after_failure),
                historyAfterFailure.map { it.migration to it.batch },
                failedId = migrationTags.broken_migration,
                notExecutedIds = setOf(migrationTags.migration_after_failure),
            )
            assertEquals(
                expectedSuccessfulIds + listOf(
                    migrationTags.failure_create_users,
                    migrationTags.failure_add_email,
                ),
                historyAfterFailure.map(HistoryRow::migration),
            )
            assertEquals(listOf(1, 1, 1, 1, 2, 2), historyAfterFailure.map(HistoryRow::batch))
            assertFalse(historyAfterFailure.any { it.migration == migrationTags.broken_migration })
            assertFalse(historyAfterFailure.any { it.migration == migrationTags.migration_after_failure })
            assertFalse(AFTER_FAILURE_TABLE in tableNames(testConnection))
            runtime?.succeedStep("failure-resume", mapOf("failureObserved" to true, "resumePreservedHistory" to true))

            runtime?.startStep("operation-validation")
            val resumedFailure = assertIs<ExecuteResult.Failure>(failureMigrator.migrateAndCapture())
            assertTrue(resumedFailure.exception.message.orEmpty().contains(FAILURE_BLOCKER_TABLE))
            val historyAfterRetry = migrationHistory()
            assertEquals(historyAfterFailure, historyAfterRetry)
            assertFalse(historyAfterRetry.any { it.batch == 3 })
            assertFalse(AFTER_FAILURE_TABLE in tableNames(testConnection))

            val emptyOperationConfiguration = migrationConfig {
                migration<EmptyOperationMigration>()
                migration<AfterEmptyOperation>()
            }
            assertIs<ExecuteResult.Failure>(MigrationMigrator(emptyOperationConfiguration).migrateAndCapture())
            assertEquals(historyAfterFailure, migrationHistory())
            assertFalse(AFTER_EMPTY_TABLE in tableNames(testConnection))
            assertFalse(migrationHistory().any {
                it.migration == migrationTags.empty_operation || it.migration == migrationTags.after_empty_operation
            })

            val multipleOperationsConfiguration = migrationConfig {
                migration<MultipleOperationsMigration>()
                migration<AfterMultipleOperations>()
            }
            assertIs<ExecuteResult.Failure>(MigrationMigrator(multipleOperationsConfiguration).migrateAndCapture())
            assertEquals(historyAfterFailure, migrationHistory())
            assertFalse(MULTI_OPERATION_TABLE_ONE in tableNames(testConnection))
            assertFalse(MULTI_OPERATION_TABLE_TWO in tableNames(testConnection))
            assertFalse(AFTER_MULTIPLE_TABLE in tableNames(testConnection))
            assertFalse(migrationHistory().any {
                it.migration == migrationTags.multiple_operations ||
                    it.migration == migrationTags.after_multiple_operations
            })
            runtime?.recordMigrations(
                listOf(migrationTags.empty_operation, migrationTags.after_empty_operation),
                historyRows = migrationHistory().map { it.migration to it.batch },
                failedId = migrationTags.empty_operation,
                notExecutedIds = setOf(migrationTags.after_empty_operation),
            )
            runtime?.recordMigrations(
                listOf(migrationTags.multiple_operations, migrationTags.after_multiple_operations),
                historyRows = migrationHistory().map { it.migration to it.batch },
                failedId = migrationTags.multiple_operations,
                notExecutedIds = setOf(migrationTags.after_multiple_operations),
            )
            runtime?.succeedStep("operation-validation", mapOf("zeroAndMultipleOperationRejected" to true))

            runtime?.startStep("final-state")
            val finalTables = tableNames(testConnection)
            assertTrue(TEST_USERS_TABLE in finalTables)
            assertTrue(TEST_POSTS_TABLE in finalTables)
            assertTrue(FAILURE_BLOCKER_TABLE in finalTables)
            assertTrue(FAILURE_USERS_TABLE in finalTables)
            assertTrue(SYSTEM_MIGRATION_TABLE in finalTables)
            assertEquals(historyAfterFailure, migrationHistory())
            runtime?.recordHistory(historyAfterFailure.map { it.migration to it.batch })
            runtime?.recordSchema(
                TestRuntimeRecorder.captureSchema(testConnection, ownedFixtureTables.filter { it in finalTables }),
                mapOf("tables" to finalTables.filter { it in ownedFixtureTables || it == SYSTEM_MIGRATION_TABLE }.sorted()),
            )
            runtime?.succeedStep("final-state", mapOf("historyRows" to historyAfterFailure.size))
        } catch (failure: Throwable) {
            testFailure = failure
            runtime?.failActiveStep()
            throw failure
        } finally {
            var lifecycleFailure: Throwable? = null
            runtime?.startStep("restore-resources")
            fun captureLifecycle(action: () -> Unit) {
                try {
                    action()
                } catch (failure: Throwable) {
                    val previousFailure = lifecycleFailure
                    if (previousFailure == null) lifecycleFailure = failure
                    else previousFailure.addSuppressed(failure)
                }
            }

            // Database state is intentionally preserved for developer inspection.
            captureLifecycle {
                val restore = restoreTestConfig
                if (restore == null) DefaultDatabaseConfig.config = previousConfig else restore()
            }
            captureLifecycle { connection?.close() }

            if (lifecycleFailure == null) runtime?.succeedStep("restore-resources") else runtime?.failActiveStep()
            if (testFailure == null && lifecycleFailure == null) runtime?.complete() else runtime?.incomplete()
            lifecycleFailure?.let { cleanup ->
                val originalFailure = testFailure
                if (originalFailure == null) throw cleanup
                originalFailure.addSuppressed(cleanup)
            }
        }
        } catch (failure: Throwable) {
            runtime?.failActiveStep()
            runtime?.incomplete()
            throw failure
        }
    }

    private fun requiredNonBlankProperty(name: String): String =
        (System.getProperty(name) ?: error("Missing required Gradle property '$name'"))
            .takeIf(String::isNotBlank)
            ?: error("Gradle property '$name' must not be blank")

    private fun parseRefreshDatabaseOption(value: String?): Boolean = when (value) {
        null, "false" -> false
        "true" -> true
        else -> error("Gradle property '$DB_REFRESH_PROPERTY' must be exactly 'true' or 'false'")
    }

    private fun runConfiguredRefresh(enabled: Boolean, refresh: () -> Unit) {
        if (enabled) refresh()
    }

    private fun installTestConfig(config: DatabaseConfig): () -> Unit {
        val previousConfig = DefaultDatabaseConfig.config
        var restored = false
        DefaultDatabaseConfig.config = config
        return {
            if (!restored) {
                DefaultDatabaseConfig.config = previousConfig
                restored = true
            }
        }
    }

    private fun refreshBeforeMigrationLifecycle(
        enabled: Boolean,
        restoreTestConfig: () -> Unit,
        refresh: () -> Unit,
    ) {
        try {
            runConfiguredRefresh(enabled, refresh)
        } catch (failure: Throwable) {
            try {
                restoreTestConfig()
            } catch (restoreFailure: Throwable) {
                failure.addSuppressed(restoreFailure)
            }
            throw failure
        }
    }

    private fun refreshAllTables(connection: Connection) {
        assertSelectedCatalog(connection, requiredNonBlankProperty(DB_NAME_PROPERTY))
        val tableSnapshot = tableNames(connection).toList()
        val foreignKeys = tableSnapshot.flatMap { table -> importedForeignKeys(connection, table) }.distinct()

        foreignKeys.forEach(::dropForeignKey)
        tableSnapshot.forEach(::dropTableIfPresent)
        assertTrue(tableNames(connection).isEmpty(), "Database refresh must leave no user tables")
    }

    private fun importedForeignKeys(connection: Connection, table: String): List<ForeignKeyToDrop> =
        connection.metaData.getImportedKeys(connection.catalog, null, table).use { rows ->
            buildList {
                while (rows.next()) {
                    val foreignKeyName = rows.getString("FK_NAME")
                        ?.takeIf(String::isNotBlank)
                        ?: error("Cannot safely refresh table '$table': JDBC metadata returned an unnamed foreign key")
                    add(ForeignKeyToDrop(table, foreignKeyName))
                }
            }.distinct()
        }

    private fun dropForeignKey(foreignKey: ForeignKeyToDrop) {
        var result: ExecuteResult<Boolean>? = null
        MigrationExecutor().execute(
            queryBuilder = buildDropForeignKey {
                tableName(foreignKey.table)
                name(foreignKey.name)
            },
            blockExecute = { result = it },
        )
        when (val completed = assertNotNull(result, "DROP FOREIGN KEY did not complete for " + foreignKey)) {
            is ExecuteResult.Failure -> throw AssertionError("Could not drop foreign key " + foreignKey, completed.exception)
            is ExecuteResult.Success -> assertEquals(true, completed.result, "DROP FOREIGN KEY did not succeed for " + foreignKey)
        }
    }

    private fun dropTableIfPresent(tableName: String) {
        var result: ExecuteResult<Boolean>? = null
        MigrationExecutor().execute(
            queryBuilder = dropTable {
                tableName(tableName)
                ifExists()
            },
            blockExecute = { result = it },
        )
        when (val completed = assertNotNull(result, "DROP TABLE did not complete for " + tableName)) {
            is ExecuteResult.Failure -> throw AssertionError("Could not drop table " + tableName, completed.exception)
            is ExecuteResult.Success -> Unit // Success(false) is valid for IF EXISTS.
        }
    }

    private fun assertSelectedCatalog(connection: Connection, expectedDatabase: String) {
        assertTrue(
            connection.catalog?.equals(expectedDatabase, ignoreCase = true) == true,
            "Refresh connection is not scoped to DatabaseConfig.dbName '$expectedDatabase'",
        )
    }

    private data class ForeignKeyToDrop(val table: String, val name: String)

    private fun assertNoOwnedTableCollisions(connection: Connection) {
        val existingTables = tableNames(connection)
        val collisions = allOwnedTableNames.filter { requested ->
            existingTables.any { it.equals(requested, ignoreCase = true) }
        }
        assertTrue(
            collisions.isEmpty(),
            "Dedicated integration database contains migration test table(s) $collisions; refusing to touch them",
        )
    }

    private fun existingOwnedObjects(connection: Connection): List<String> {
        val presentTables = tableNames(connection).map { it.lowercase() }.toSet()
        return allOwnedTableNames.filter { it.lowercase() in presentTables }
    }

    private fun migrationHistory(): List<HistoryRow> {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableColumn("migration_history", "id") }
                    alias("id")
                }
                addColumn {
                    column { tableColumn("migration_history", "migration") }
                    alias("migration")
                }
                addColumn {
                    column { tableColumn("migration_history", "batch") }
                    alias("batch")
                }
            }
            .table { table("system_migration").alias("migration_history") }
            .order {
                orderAsc()
                addColumn { tableColumn("migration_history", "id") }
            }

        var executionResult: ExecuteResult<List<HistoryRow>>? = null
        QueryBuilderExecutor().execute(
            queryBuilder = query,
            blockExecute = { result ->
                executionResult = when (result) {
                    is ExecuteResult.Failure -> result
                    is ExecuteResult.Success -> ExecuteResult.Success(
                        assertNotNull(result.result, "History SELECT returned no ResultSet").use { rows ->
                            buildList {
                                while (rows.next()) {
                                    add(HistoryRow(rows.getInt("id"), rows.getString("migration"), rows.getInt("batch")))
                                }
                            }
                        },
                    )
                }
            },
            blockQueryInfo = null,
        )

        return when (val result = assertNotNull(executionResult, "History SELECT did not complete synchronously")) {
            is ExecuteResult.Failure -> throw AssertionError("Could not read system_migration history", result.exception)
            is ExecuteResult.Success -> assertNotNull(result.result, "History SELECT returned no rows")
        }
    }

    private fun MigrationMigrator.migrateAndCapture(): ExecuteResult<Unit> {
        var result: ExecuteResult<Unit>? = null
        migrate { actual ->
            check(result == null) { "MigrationMigrator completed more than once" }
            result = actual
        }
        return assertNotNull(result, "MigrationMigrator did not complete synchronously")
    }

    private fun tableNames(connection: Connection): Set<String> {
        val rows = connection.metaData.getTables(connection.catalog, null, "%", arrayOf("TABLE"))
        return rows.use { result -> buildSet { while (result.next()) add(result.getString("TABLE_NAME")) } }
    }

    private fun columnNames(connection: Connection, tableName: String): Set<String> {
        val rows = connection.metaData.getColumns(connection.catalog, null, "%", "%")
        return rows.use { result ->
            buildSet {
                while (result.next()) {
                    if (result.getString("TABLE_NAME").equals(tableName, ignoreCase = true)) {
                        add(result.getString("COLUMN_NAME"))
                    }
                }
            }
        }
    }

    private data class HistoryRow(val id: Int, val migration: String, val batch: Int)

    companion object {
        private const val DB_DOMAIN_PROPERTY = "migration.test.db.domain"
        private const val DB_PORT_PROPERTY = "migration.test.db.port"
        private const val DB_NAME_PROPERTY = "migration.test.db.name"
        private const val DB_USERNAME_PROPERTY = "migration.test.db.username"
        private const val DB_PASSWORD_PROPERTY = "migration.test.db.password"
        private const val DB_REFRESH_PROPERTY = "migration.test.db.refresh"
        private const val SYSTEM_MIGRATION_TABLE = "system_migration"

        const val TEST_USERS_TABLE = "migration_test_users"
        const val TEST_POSTS_TABLE = "migration_test_posts"
        const val FAILURE_USERS_TABLE = "migration_test_failure_users"
        const val FAILURE_BLOCKER_TABLE = "migration_test_existing_table"
        const val AFTER_FAILURE_TABLE = "migration_test_after_failure"
        const val AFTER_EMPTY_TABLE = "migration_test_after_empty"
        const val MULTI_OPERATION_TABLE_ONE = "migration_test_multi_one"
        const val MULTI_OPERATION_TABLE_TWO = "migration_test_multi_two"
        const val AFTER_MULTIPLE_TABLE = "migration_test_after_multiple"

        val ownedFixtureTables = listOf(
            TEST_USERS_TABLE,
            TEST_POSTS_TABLE,
            FAILURE_USERS_TABLE,
            FAILURE_BLOCKER_TABLE,
            AFTER_FAILURE_TABLE,
            AFTER_EMPTY_TABLE,
            MULTI_OPERATION_TABLE_ONE,
            MULTI_OPERATION_TABLE_TWO,
            AFTER_MULTIPLE_TABLE,
        )

        val allOwnedTableNames = ownedFixtureTables + SYSTEM_MIGRATION_TABLE
    }
}

private object migrationTags {
    const val create_test_users = "create_test_users"
    const val add_test_users_email = "add_test_users_email"
    const val create_test_posts = "create_test_posts"
    const val create_failure_blocker = "create_failure_blocker"
    const val failure_create_users = "failure_create_users"
    const val failure_add_email = "failure_add_email"
    const val broken_migration = "broken_migration"
    const val migration_after_failure = "migration_after_failure"
    const val empty_operation = "empty_operation"
    const val after_empty_operation = "after_empty_operation"
    const val multiple_operations = "multiple_operations"
    const val after_multiple_operations = "after_multiple_operations"
}

@MigrationId(migrationTags.create_test_users)
class CreateTestUsers : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName(MigrationSystemIntegrationTest.TEST_USERS_TABLE) }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("name")
                dataType(VarcharType(120))
                notNull()
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.add_test_users_email)
class AddTestUsersEmail : Migration {
    override fun up(): MigrationDefinition = migration {
        addColumn {
            tableName(MigrationSystemIntegrationTest.TEST_USERS_TABLE)
            name("email")
            dataType(VarcharType(255))
            notNull()
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.create_test_posts)
class CreateTestPosts : Migration {
    override fun up(): MigrationDefinition = createTestTable(MigrationSystemIntegrationTest.TEST_POSTS_TABLE)
    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.create_failure_blocker)
class CreateFailureBlocker : Migration {
    override fun up(): MigrationDefinition = createTestTable(MigrationSystemIntegrationTest.FAILURE_BLOCKER_TABLE)
    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.failure_create_users)
class FailureCreateUsers : Migration {
    override fun up(): MigrationDefinition = createTestTable(MigrationSystemIntegrationTest.FAILURE_USERS_TABLE)
    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.failure_add_email)
class FailureAddEmail : Migration {
    override fun up(): MigrationDefinition = migration {
        addColumn {
            tableName(MigrationSystemIntegrationTest.FAILURE_USERS_TABLE)
            name("email")
            dataType(VarcharType(255))
            notNull()
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.broken_migration)
class BrokenMigration : Migration {
    override fun up(): MigrationDefinition = createTestTable(MigrationSystemIntegrationTest.FAILURE_BLOCKER_TABLE)
    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.migration_after_failure)
class MigrationAfterFailure : Migration {
    override fun up(): MigrationDefinition = createTestTable(MigrationSystemIntegrationTest.AFTER_FAILURE_TABLE)

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.empty_operation)
class EmptyOperationMigration : Migration {
    override fun up(): MigrationDefinition = migration { }
    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.after_empty_operation)
class AfterEmptyOperation : Migration {
    override fun up(): MigrationDefinition = createTestTable(MigrationSystemIntegrationTest.AFTER_EMPTY_TABLE)
    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.multiple_operations)
class MultipleOperationsMigration : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName(MigrationSystemIntegrationTest.MULTI_OPERATION_TABLE_ONE) }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
            }
        }
        createTable {
            table { tableName(MigrationSystemIntegrationTest.MULTI_OPERATION_TABLE_TWO) }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.after_multiple_operations)
class AfterMultipleOperations : Migration {
    override fun up(): MigrationDefinition = createTestTable(MigrationSystemIntegrationTest.AFTER_MULTIPLE_TABLE)
    override fun down(): MigrationDefinition = migration { }
}

private fun createTestTable(name: String): MigrationDefinition = migration {
    createTable {
        table { tableName(name) }
        addColumn {
            name("id")
            dataType(IntType())
            notNull()
        }
    }
}
