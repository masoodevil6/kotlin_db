package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnostic
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnosticStage
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureCheckpointWriteCommand
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusCapability
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusCommand
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusWriteResult
import gog.my_project.data_base_operation.migration.failure.spi.write
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationFailureStatusCapabilityTest {
    @Test
    fun `capability callback is not invoked when Migration lock acquisition fails`() {
        val script = JdbcScript(acquireValue = 0)
        var callbackInvoked = false

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(0) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertFalse(callbackInvoked)
        assertTrue(script.updates.isEmpty())
        assertFalse(script.sql.any { it.startsWith("SELECT RELEASE_LOCK") })
    }

    @Test
    fun `fixed failure and manual recovery transitions are emitted on the owning Connection`() {
        data class Transition(
            val command: MigrationFailureStatusCommand,
            val target: Int,
            val expected: Int,
            val stage: MigrationFailureDiagnosticStage,
        )

        val transitions = listOf(
            Transition(MigrationFailureStatusCommand.UpExecutionFailure(11), 101, 100, MigrationFailureDiagnosticStage.UP_EXECUTION),
            Transition(MigrationFailureStatusCommand.UpRecordingFailure(12), 102, 100, MigrationFailureDiagnosticStage.UP_RECORDING),
            Transition(MigrationFailureStatusCommand.DownExecutionFailure(13), 201, 200, MigrationFailureDiagnosticStage.DOWN_EXECUTION),
            Transition(MigrationFailureStatusCommand.DownRecordingFailure(14), 202, 200, MigrationFailureDiagnosticStage.DOWN_RECORDING),
            Transition(MigrationFailureStatusCommand.UpManualRecoveryRequired(15), 110, 100, MigrationFailureDiagnosticStage.UP_RECORDING),
            Transition(MigrationFailureStatusCommand.DownManualRecoveryRequired(16), 210, 200, MigrationFailureDiagnosticStage.DOWN_RECORDING),
        )

        transitions.forEach { transition ->
            val (command, target, expected, stage) = transition
            val script = JdbcScript()
            val primary = IllegalStateException("migration failed")
            val diagnostic = MigrationFailureDiagnostic(
                stage = stage,
                exceptionClassName = primary.javaClass.name,
                sqlState = null,
                vendorErrorCode = null,
                message = "migration failed",
            )
            var writeResult: MigrationFailureStatusWriteResult? = null

            val result = runManaged(script) { scope ->
                scope.withMigrationFailureStatusCapability(1) { capability ->
                    scope.recordFailure(primary)
                    writeResult = capability.write(command, diagnostic)
                    ExecuteResult.Success(Unit)
                }
            }

            assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(MigrationFailureStatusWriteResult.APPLIED, writeResult)
            val update = script.updates.single()
            assertEquals(
                "UPDATE system_migration_version SET status = ?, message = ? WHERE id = ? AND status = ?",
                update.statement.sql,
            )
            assertEquals(
                listOf(target, diagnostic.toStorageMessage(command.versionId), command.versionId, expected),
                update.bindings.map { it.value },
            )
            assertEquals(listOf(1, 2, 3, 4), update.bindings.map { it.index })
            assertEquals(
                listOf(Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.INTEGER),
                update.bindings.map { it.sqlType },
            )
            assertTrue(script.usedConnections.all { it === script.connection })
        }
    }

    @Test
    fun `checkpoint capability emits each closed status and checkpoint CAS on the owning Connection`() {
        data class Transition(
            val command: MigrationFailureCheckpointWriteCommand,
            val targetStatus: Int,
            val expectedStatus: Int,
        )

        val diagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_RECORDING,
            exceptionClassName = "java.sql.SQLException",
            sqlState = "42000",
            vendorErrorCode = 1064,
            message = "migration metadata write failed",
            migrationId = "create_tables",
        ).toStorageMessage(71)
        val expectedCheckpoint = "{\"version\":\"expected\"}"
        val updatedCheckpoint = "{\"version\":\"updated\"}"
        val transitions = listOf(
            Transition(
                MigrationFailureCheckpointWriteCommand.UpExecutionUncertain(71, expectedCheckpoint, updatedCheckpoint, diagnostic),
                110,
                100,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.UpExecutionFailure(79, expectedCheckpoint, updatedCheckpoint, diagnostic),
                101,
                100,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.UpReconciliationInconsistent(72, expectedCheckpoint, updatedCheckpoint, diagnostic),
                110,
                100,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.UpRecordingFailure(73, expectedCheckpoint, updatedCheckpoint, diagnostic),
                102,
                100,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.UpFinalizationFailure(74, expectedCheckpoint, updatedCheckpoint, diagnostic),
                102,
                100,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.DownExecutionUncertain(75, expectedCheckpoint, updatedCheckpoint, diagnostic),
                210,
                200,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.DownExecutionFailure(80, expectedCheckpoint, updatedCheckpoint, diagnostic),
                201,
                200,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.DownReconciliationInconsistent(76, expectedCheckpoint, updatedCheckpoint, diagnostic),
                210,
                200,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.DownRecordingFailure(77, expectedCheckpoint, updatedCheckpoint, diagnostic),
                202,
                200,
            ),
            Transition(
                MigrationFailureCheckpointWriteCommand.DownFinalizationFailure(78, expectedCheckpoint, updatedCheckpoint, diagnostic),
                202,
                200,
            ),
        )

        transitions.forEach { transition ->
            val script = JdbcScript()
            val primary = IllegalStateException("latched Migration failure")
            var writeResult: MigrationFailureStatusWriteResult? = null

            val result = runManaged(script) { scope ->
                scope.withMigrationFailureCheckpointCapability(1) { capability ->
                    scope.recordFailure(primary)
                    writeResult = capability.writeCheckpoint(transition.command)
                    ExecuteResult.Success(Unit)
                }
            }

            assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(MigrationFailureStatusWriteResult.APPLIED, writeResult)
            val update = script.updates.single()
            assertEquals(
                "UPDATE system_migration_version SET status = ?, message = ?, retry_checkpoint = ? " +
                    "WHERE id = ? AND status = ? AND CAST(retry_checkpoint AS BINARY) = CAST(? AS BINARY)",
                update.statement.sql,
            )
            assertEquals(
                listOf(
                    transition.targetStatus,
                    diagnostic,
                    updatedCheckpoint,
                    transition.command.versionId,
                    transition.expectedStatus,
                    expectedCheckpoint,
                ),
                update.bindings.map { it.value },
            )
            assertEquals(listOf(1, 2, 3, 4, 5, 6), update.bindings.map { it.index })
            assertEquals(
                listOf(Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.VARCHAR),
                update.bindings.map { it.sqlType },
            )
            assertTrue(script.usedConnections.all { it === script.connection })
        }
    }

    @Test
    fun `legacy and checkpoint capability methods consume one shared one shot state`() {
        listOf(true, false).forEach { checkpointFirst ->
            val script = JdbcScript()
            val primary = IllegalStateException("latched failure")
            var firstResult: MigrationFailureStatusWriteResult? = null
            var duplicateFailure: Throwable? = null

            val result = runManaged(script) { scope ->
                scope.withMigrationFailureCheckpointCapability(1) { capability ->
                    scope.recordFailure(primary)
                    firstResult = if (checkpointFirst) {
                        capability.writeCheckpoint(checkpointCommand())
                    } else {
                        capability.write(MigrationFailureStatusCommand.UpExecutionFailure(81))
                    }
                    duplicateFailure = runCatching {
                        if (checkpointFirst) {
                            capability.write(MigrationFailureStatusCommand.UpExecutionFailure(81))
                        } else {
                            capability.writeCheckpoint(checkpointCommand())
                        }
                    }.exceptionOrNull()
                    ExecuteResult.Success(Unit)
                }
            }

            assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(MigrationFailureStatusWriteResult.APPLIED, firstResult)
            assertIs<IllegalStateException>(duplicateFailure)
            assertEquals(1, script.updates.size)
        }
    }

    @Test
    fun `checkpoint write failure remains secondary to the original failure`() {
        val statusWriteFailure = SQLException("checkpoint UPDATE failed")
        val script = JdbcScript(updateFailure = statusWriteFailure)
        val primary = IllegalStateException("original failure")
        var writeResult: MigrationFailureStatusWriteResult? = null

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureCheckpointCapability(1) { capability ->
                scope.recordFailure(primary)
                writeResult = capability.writeCheckpoint(checkpointCommand())
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(MigrationFailureStatusWriteResult.INDETERMINATE, writeResult)
        assertTrue(primary.suppressed.any { it === statusWriteFailure })
        assertEquals(1, script.updates.size)
    }

    @Test
    fun `fatal checkpoint UPDATE error preserves exact identity and original failure`() {
        val fatal = AssertionError("fatal checkpoint UPDATE")
        val script = JdbcScript(updateFailure = fatal)
        val primary = IllegalStateException("original failure")

        val thrown = assertFailsWith<AssertionError> {
            runManaged(script) { scope ->
                scope.withMigrationFailureCheckpointCapability(1) { capability ->
                    scope.recordFailure(primary)
                    capability.writeCheckpoint(checkpointCommand())
                    ExecuteResult.Success(Unit)
                }
            }
        }

        assertSame(fatal, thrown)
        assertTrue(fatal.suppressed.any { it === primary })
        assertEquals(1, script.updates.size)
    }

    @Test
    fun `diagnostic with absent message retains structured fields and legacy write stays status only`() {
        val diagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_EXECUTION,
            exceptionClassName = "java.sql.SQLException",
            sqlState = "42S21",
            vendorErrorCode = 1060,
            message = "  \n\t ",
        )
        assertEquals(
            """{"schemaVersion":1,"stage":"UP_EXECUTION","versionId":null,"migrationId":null,"exception":"java.sql.SQLException","sqlState":"42S21","errorCode":1060,"message":""}""",
            diagnostic.toStorageMessage(),
        )
        val nullMessageDiagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_EXECUTION,
            exceptionClassName = "java.sql.SQLException",
            sqlState = "42S21",
            vendorErrorCode = 1060,
            message = null,
        )
        assertEquals(diagnostic.toStorageMessage(), nullMessageDiagnostic.toStorageMessage())

        val script = JdbcScript()
        val primary = IllegalStateException("migration failed")
        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(1) { capability ->
                scope.recordFailure(primary)
                assertEquals(
                    MigrationFailureStatusWriteResult.APPLIED,
                    capability.write(MigrationFailureStatusCommand.UpExecutionFailure(21)),
                )
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals("UPDATE system_migration_version SET status = ? WHERE id = ? AND status = ?", script.updates.single().statement.sql)
        assertEquals(listOf(101, 21, 100), script.updates.single().bindings.map { it.value })
    }

    @Test
    fun `diagnostic stores command Version ID and optional Migration ID`() {
        val diagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_EXECUTION,
            exceptionClassName = "java.sql.SQLException",
            sqlState = "42S21",
            vendorErrorCode = 1060,
            message = "Duplicate column name display_name",
            migrationId = "create_manager_customers",
        )

        assertEquals("create_manager_customers", diagnostic.migrationId)
        assertEquals(
            """{"schemaVersion":1,"stage":"UP_EXECUTION","versionId":76,"migrationId":"create_manager_customers","exception":"java.sql.SQLException","sqlState":"42S21","errorCode":1060,"message":"Duplicate column name display_name"}""",
            diagnostic.toStorageMessage(76),
        )
        assertEquals(
            """{"schemaVersion":1,"stage":"UP_EXECUTION","versionId":null,"migrationId":"create_manager_customers","exception":"java.sql.SQLException","sqlState":"42S21","errorCode":1060,"message":"Duplicate column name display_name"}""",
            diagnostic.toStorageMessage(),
        )
    }

    @Test
    fun `invalid Migration ID is omitted without blocking the latched status write`() {
        val script = JdbcScript()
        val primary = IllegalStateException("primary migration failure")
        val diagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_EXECUTION,
            exceptionClassName = primary.javaClass.name,
            sqlState = null,
            vendorErrorCode = null,
            message = primary.message,
            migrationId = "create_users\nstage=INJECTED",
        )
        assertNull(diagnostic.migrationId)

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(1) { capability ->
                scope.recordFailure(primary)
                assertEquals(
                    MigrationFailureStatusWriteResult.APPLIED,
                    capability.write(MigrationFailureStatusCommand.UpExecutionFailure(77), diagnostic),
                )
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        val stored = script.updates.single().bindings[1].value as String
        assertEquals(
            """{"schemaVersion":1,"stage":"UP_EXECUTION","versionId":77,"migrationId":null,"exception":"java.lang.IllegalStateException","sqlState":null,"errorCode":null,"message":"primary migration failure"}""",
            stored,
        )
    }

    @Test
    fun `original public diagnostic constructor JVM signature remains available`() {
        val legacyConstructor = MigrationFailureDiagnostic::class.java.getConstructor(
            MigrationFailureDiagnosticStage::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaObjectType,
            String::class.java,
        )

        val diagnostic = legacyConstructor.newInstance(
            MigrationFailureDiagnosticStage.DOWN_RECORDING,
            "java.sql.SQLException",
            "42000",
            1064,
            "legacy diagnostic",
        )

        assertNull(diagnostic.migrationId)
        assertEquals(
            """{"schemaVersion":1,"stage":"DOWN_RECORDING","versionId":null,"migrationId":null,"exception":"java.sql.SQLException","sqlState":"42000","errorCode":1064,"message":"legacy diagnostic"}""",
            diagnostic.toStorageMessage(),
        )
    }

    @Test
    fun `diagnostic redacts common secrets SQL text and enforces the storage length`() {
        val secretDiagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.DOWN_RECORDING,
            exceptionClassName = "invalid class name",
            sqlState = "not a sqlstate",
            vendorErrorCode = 9,
            message = "password=hunter2 token:abc Bearer bearer-secret jdbc:mysql://db.internal/catalog?user=alice&password=secret",
        )
        val stored = secretDiagnostic.toStorageMessage()
        assertTrue(stored.contains("\"stage\":\"DOWN_RECORDING\""))
        assertTrue(stored.contains("\"exception\":\"unknown\""))
        assertTrue(stored.contains("\"errorCode\":9"))
        assertFalse(stored.contains("hunter2"))
        assertFalse(stored.contains("bearer-secret"))
        assertFalse(stored.contains("db.internal"))

        val sqlDiagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.DOWN_RECORDING,
            exceptionClassName = "java.sql.SQLException",
            sqlState = null,
            vendorErrorCode = null,
            message = "SQL syntax error near CREATE VIEW private_value AS SELECT secret FROM account",
        ).toStorageMessage()
        assertTrue(sqlDiagnostic.contains("[SQL text redacted]"))
        assertFalse(sqlDiagnostic.contains("private_value"))

        val longDiagnostic = MigrationFailureDiagnostic(
            stage = MigrationFailureDiagnosticStage.UP_RECORDING,
            exceptionClassName = "example.Failure",
            sqlState = null,
            vendorErrorCode = null,
            message = "x".repeat(3000),
        ).toStorageMessage()
        assertTrue(longDiagnostic.codePointCount(0, longDiagnostic.length) <= MigrationFailureDiagnostic.MAX_STORAGE_MESSAGE_LENGTH)
    }

    @Test
    fun `diagnostic extension preserves compatibility with status only capability implementations`() {
        var received: MigrationFailureStatusCommand? = null
        val legacyCapability = object : MigrationFailureStatusCapability {
            override fun write(command: MigrationFailureStatusCommand): MigrationFailureStatusWriteResult {
                received = command
                return MigrationFailureStatusWriteResult.APPLIED
            }
        }
        val command = MigrationFailureStatusCommand.DownRecordingFailure(31)
        val diagnostic = MigrationFailureDiagnostic(
            MigrationFailureDiagnosticStage.DOWN_RECORDING,
            "java.sql.SQLException",
            "42000",
            1064,
            "failure",
        )

        assertEquals(MigrationFailureStatusWriteResult.APPLIED, legacyCapability.write(command, diagnostic))
        assertSame(command, received)
    }

    @Test
    fun `write requires a latched ordinary failure and ordinary DML stays blocked`() {
        val script = JdbcScript()
        val primary = IllegalStateException("primary failure")
        var prematureFailure: Throwable? = null
        var ordinaryDmlResult: ExecuteResult<Int>? = null
        var statusResult: MigrationFailureStatusWriteResult? = null

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(1) { capability ->
                prematureFailure = runCatching {
                    capability.write(MigrationFailureStatusCommand.UpExecutionFailure(23))
                }.exceptionOrNull()
                assertTrue(script.updates.isEmpty())

                scope.recordFailure(primary)
                ordinaryDmlResult = scope.executeUpdate(BoundQuery("UPDATE arbitrary_table SET x = 1", emptyList()))
                statusResult = capability.write(MigrationFailureStatusCommand.UpExecutionFailure(23))
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<IllegalStateException>(prematureFailure)
        assertSame(primary, assertIs<ExecuteResult.Failure>(ordinaryDmlResult).exception)
        assertEquals(MigrationFailureStatusWriteResult.APPLIED, statusResult)
        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertFalse(script.sql.any { it.contains("arbitrary_table") })
        assertEquals(1, script.updates.size)
    }

    @Test
    fun `affected row counts map to applied not applied and indeterminate without retry`() {
        listOf(
            1 to MigrationFailureStatusWriteResult.APPLIED,
            0 to MigrationFailureStatusWriteResult.NOT_APPLIED,
            2 to MigrationFailureStatusWriteResult.INDETERMINATE,
        ).forEach { (rowCount, expected) ->
            val script = JdbcScript(affectedRows = rowCount)
            val primary = IllegalStateException("migration failed")
            var firstResult: MigrationFailureStatusWriteResult? = null
            var secondAttemptFailure: Throwable? = null

            runManaged(script) { scope ->
                scope.withMigrationFailureStatusCapability(1) { capability ->
                    scope.recordFailure(primary)
                    firstResult = capability.write(MigrationFailureStatusCommand.DownExecutionFailure(31))
                    secondAttemptFailure = runCatching {
                        capability.write(MigrationFailureStatusCommand.DownRecordingFailure(31))
                    }.exceptionOrNull()
                    ExecuteResult.Success(Unit)
                }
            }

            assertEquals(expected, firstResult)
            assertIs<IllegalStateException>(secondAttemptFailure)
            if (rowCount == 0) assertTrue(primary.suppressed.isEmpty())
            assertEquals(1, script.updates.size)
        }
    }

    @Test
    fun `capability expires before Release and after Scope finalization`() {
        val script = JdbcScript()
        val primary = IllegalStateException("migration failed")
        var capability: MigrationFailureStatusCapability? = null
        var releaseUseFailure: Throwable? = null
        script.beforeReleasePrepare = {
            releaseUseFailure = runCatching {
                requireNotNull(capability).write(MigrationFailureStatusCommand.UpRecordingFailure(40))
            }.exceptionOrNull()
        }

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(1) { issued ->
                capability = issued
                scope.recordFailure(primary)
                assertEquals(
                    MigrationFailureStatusWriteResult.APPLIED,
                    issued.write(MigrationFailureStatusCommand.UpExecutionFailure(40)),
                )
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertIs<IllegalStateException>(releaseUseFailure)
        assertFailsWith<IllegalStateException> {
            requireNotNull(capability).write(MigrationFailureStatusCommand.UpRecordingFailure(40))
        }
        assertEquals(1, script.updates.size)
        assertTrue(script.sql.indexOfFirst { it.startsWith("SELECT RELEASE_LOCK") } >
            script.sql.indexOfFirst { it.startsWith("UPDATE system_migration_version") })
    }

    @Test
    fun `old capability cannot be used in a later lock lease`() {
        val oldScript = JdbcScript()
        val oldPrimary = IllegalStateException("first failure")
        var oldCapability: MigrationFailureStatusCapability? = null
        runManaged(oldScript) { scope ->
            scope.withMigrationFailureStatusCapability(1) { issued ->
                oldCapability = issued
                scope.recordFailure(oldPrimary)
                ExecuteResult.Success(Unit)
            }
        }

        val newScript = JdbcScript()
        val newPrimary = IllegalStateException("second failure")
        var staleFailure: Throwable? = null
        val result = runManaged(newScript) { scope ->
            scope.withMigrationFailureStatusCapability(1) { current ->
                scope.recordFailure(newPrimary)
                staleFailure = runCatching {
                    requireNotNull(oldCapability).write(MigrationFailureStatusCommand.UpExecutionFailure(50))
                }.exceptionOrNull()
                assertEquals(
                    MigrationFailureStatusWriteResult.APPLIED,
                    current.write(MigrationFailureStatusCommand.UpExecutionFailure(51)),
                )
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<IllegalStateException>(staleFailure)
        assertSame(newPrimary, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(oldScript.updates.isEmpty())
        assertEquals(listOf(101, 51, 100), newScript.updates.single().bindings.map { it.value })
    }

    @Test
    fun `cross thread capability use is rejected without consuming owner thread use`() {
        val script = JdbcScript()
        val primary = IllegalStateException("migration failed")
        var crossThreadFailure: Throwable? = null
        var ownerThreadResult: MigrationFailureStatusWriteResult? = null

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(1) { capability ->
                scope.recordFailure(primary)
                val worker = thread(start = true) {
                    crossThreadFailure = runCatching {
                        capability.write(MigrationFailureStatusCommand.DownExecutionFailure(60))
                    }.exceptionOrNull()
                }
                worker.join()
                assertTrue(script.updates.isEmpty())
                ownerThreadResult = capability.write(MigrationFailureStatusCommand.DownExecutionFailure(60))
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<IllegalStateException>(crossThreadFailure)
        assertEquals(MigrationFailureStatusWriteResult.APPLIED, ownerThreadResult)
        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, script.updates.size)
    }

    @Test
    fun `active child Scope rejects capability until child is restored`() {
        val script = JdbcScript()
        val primary = IllegalStateException("migration failed")
        var childRejection: Throwable? = null
        var postChildResult: MigrationFailureStatusWriteResult? = null

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(1) { capability ->
                scope.recordFailure(primary)
                val child = OperationExecutionScope(
                    scope.lifecycleConnection(),
                    scope.databasePlatformConfig,
                    scope.databaseIdentity,
                    scope.eventListener,
                    scope.operationFailureOwner,
                )
                scope.suspendForChild(child)
                try {
                    childRejection = runCatching {
                        capability.write(MigrationFailureStatusCommand.DownRecordingFailure(70))
                    }.exceptionOrNull()
                } finally {
                    scope.restoreAfterChild(child)
                    child.endForLifecycle()
                }
                postChildResult = capability.write(MigrationFailureStatusCommand.DownRecordingFailure(70))
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<IllegalStateException>(childRejection)
        assertEquals(MigrationFailureStatusWriteResult.APPLIED, postChildResult)
        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, script.updates.size)
    }

    @Test
    fun `status write exception is secondary and lock and Connection cleanup remain ordered`() {
        val statusFailure = SQLException("status update failed")
        val statementCloseFailure = SQLException("status statement close failed")
        val releaseFailure = SQLException("release failed")
        val closeFailure = SQLException("connection close failed")
        val script = JdbcScript(
            updateFailure = statusFailure,
            releaseFailure = releaseFailure,
            connectionCloseFailure = closeFailure,
            updateStatementCloseFailure = statementCloseFailure,
        )
        val primary = IllegalStateException("migration failed")
        var writeResult: MigrationFailureStatusWriteResult? = null

        val result = runManaged(script) { scope ->
            scope.withMigrationFailureStatusCapability(1) { capability ->
                scope.recordFailure(primary)
                    writeResult = capability.write(
                        MigrationFailureStatusCommand.UpExecutionFailure(80),
                        MigrationFailureDiagnostic(
                            MigrationFailureDiagnosticStage.UP_EXECUTION,
                            primary.javaClass.name,
                            null,
                            null,
                            primary.message,
                        ),
                    )
                ExecuteResult.Success(Unit)
            }
        }

        assertEquals(MigrationFailureStatusWriteResult.INDETERMINATE, writeResult)
        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        val suppressed = primary.suppressed.toList()
        assertEquals(4, suppressed.size)
        assertSame(statusFailure, suppressed[0])
        assertSame(statementCloseFailure, suppressed[1])
        assertSame(releaseFailure, suppressed[2].cause)
        assertSame(closeFailure, suppressed[3])
        assertTrue(script.sql.indexOfFirst { it.startsWith("SELECT RELEASE_LOCK") } >
            script.sql.indexOfFirst { it.startsWith("UPDATE system_migration_version") })
        assertTrue(script.events.indexOf("connection.close") > script.events.indexOf("statement.close:release"))
    }

    @Test
    fun `Fatal Error before write blocks SQL and retains exact identity`() {
        val script = JdbcScript()
        val fatal = AssertionError("fatal before status write")
        val primary = IllegalStateException("ordinary migration failure")

        val thrown = assertFailsWith<AssertionError> {
            runManaged(script) { scope ->
                scope.withMigrationFailureStatusCapability(1) { capability ->
                    scope.recordFailure(primary)
                    scope.recordFatal(fatal)
                    capability.write(
                        MigrationFailureStatusCommand.UpExecutionFailure(90),
                        MigrationFailureDiagnostic(
                            MigrationFailureDiagnosticStage.UP_EXECUTION,
                            primary.javaClass.name,
                            null,
                            null,
                            primary.message,
                        ),
                    )
                    ExecuteResult.Success(Unit)
                }
            }
        }

        assertSame(fatal, thrown)
        assertTrue(script.updates.isEmpty())
        assertTrue(fatal.suppressed.any { it === primary })
    }

    @Test
    fun `Fatal Error during status UPDATE is rethrown with its exact identity`() {
        val fatal = AssertionError("fatal during status write")
        val script = JdbcScript(updateFailure = fatal)
        val primary = IllegalStateException("ordinary migration failure")

        val thrown = assertFailsWith<AssertionError> {
            runManaged(script) { scope ->
                scope.withMigrationFailureStatusCapability(1) { capability ->
                    scope.recordFailure(primary)
                    capability.write(
                        MigrationFailureStatusCommand.DownExecutionFailure(91),
                        MigrationFailureDiagnostic(
                            MigrationFailureDiagnosticStage.DOWN_EXECUTION,
                            primary.javaClass.name,
                            null,
                            null,
                            primary.message,
                        ),
                    )
                    ExecuteResult.Success(Unit)
                }
            }
        }

        assertSame(fatal, thrown)
        assertEquals(1, script.updates.size)
        assertTrue(fatal.suppressed.any { it === primary })
        assertTrue(script.events.contains("execute:update"))
    }

    private fun <T> runManaged(
        script: JdbcScript,
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://db.internal",
                dbPort = 3306,
                dbName = "catalog",
                dbUserName = "user",
                dbPassword = "secret",
            ),
            platform = DatabasePlatformConfig(DatabaseDialect.MYSQL),
        )
        val identity = requireNotNull(DatabaseIdentity.from(config))
        val context = DatabaseContext(
            "migration-failure-status-test",
            config,
            object : IDatabaseConnection {
                override fun build(): Connection = script.connection
            },
            identity,
        )
        return try {
            OperationExecutionExecutor(context).connection(block)
        } finally {
            context.close()
        }
    }

    private fun checkpointCommand() = MigrationFailureCheckpointWriteCommand.UpRecordingFailure(
        versionId = 81,
        expectedCheckpoint = "{\"phase\":\"RECORD_PENDING\"}",
        updatedCheckpoint = "{\"phase\":\"RECORD_PENDING\",\"revision\":1}",
        diagnosticMessage = "{\"schemaVersion\":1,\"message\":\"failure\"}",
    )

    private data class Binding(val index: Int, val value: Any?, val sqlType: Int)
    private data class UpdateCall(val statement: PreparedCall, val bindings: List<Binding>)
    private data class PreparedCall(val sql: String)

    private class JdbcScript(
        var acquireValue: Int? = 1,
        var releaseValue: Int? = 1,
        var affectedRows: Int = 1,
        var updateFailure: Throwable? = null,
        var releaseFailure: Throwable? = null,
        var connectionCloseFailure: Throwable? = null,
        var updateStatementCloseFailure: Throwable? = null,
    ) {
        val sql = CopyOnWriteArrayList<String>()
        val events = CopyOnWriteArrayList<String>()
        val usedConnections = CopyOnWriteArrayList<Connection>()
        val updates = CopyOnWriteArrayList<UpdateCall>()
        var beforeReleasePrepare: (() -> Unit)? = null

        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method) {
                "prepareStatement" -> {
                    val statementSql = args!![0] as String
                    sql += statementSql
                    usedConnections += connection
                    if (statementSql.startsWith("SELECT RELEASE_LOCK")) beforeReleasePrepare?.invoke()
                    preparedStatement(statementSql)
                }
                "close" -> {
                    events += "connection.close"
                    connectionCloseFailure?.let { throw it }
                    null
                }
                "isClosed" -> false
                "equals" -> connection === args?.get(0)
                "hashCode" -> System.identityHashCode(connection)
                "toString" -> "MigrationFailureStatusTestConnection"
                else -> defaultValue(methodReturnType(method))
            }
        }

        private fun preparedStatement(statementSql: String): PreparedStatement {
            val bindings = CopyOnWriteArrayList<Binding>()
            val call = PreparedCall(statementSql)
            var lockRows: ResultSet? = null
            return proxy(PreparedStatement::class.java) { method, args ->
                when (method) {
                    "setObject" -> {
                        bindings += Binding(args!![0] as Int, args[1], args[2] as Int)
                        null
                    }
                    "setNull" -> {
                        bindings += Binding(args!![0] as Int, null, args[1] as Int)
                        null
                    }
                    "executeQuery" -> {
                        events += if (statementSql.startsWith("SELECT GET_LOCK")) "execute:acquire" else "execute:release"
                        if (statementSql.startsWith("SELECT RELEASE_LOCK")) releaseFailure?.let { throw it }
                        val value = when {
                            statementSql.startsWith("SELECT GET_LOCK") -> acquireValue
                            statementSql.startsWith("SELECT RELEASE_LOCK") -> releaseValue
                            else -> throw SQLException("Unexpected SELECT: $statementSql")
                        }
                        resultSet(value)
                    }
                    "execute" -> {
                        events += if (statementSql.startsWith("SELECT GET_LOCK")) "execute:acquire" else "execute:release"
                        if (statementSql.startsWith("SELECT RELEASE_LOCK")) releaseFailure?.let { throw it }
                        lockRows = resultSet(if (statementSql.startsWith("SELECT GET_LOCK")) acquireValue else releaseValue)
                        true
                    }
                    "getResultSet" -> lockRows
                    "getMoreResults" -> false
                    "getUpdateCount" -> -1
                    "executeUpdate" -> {
                        events += "execute:update"
                        updates += UpdateCall(call, bindings.toList())
                        updateFailure?.let { throw it }
                        affectedRows
                    }
                    "close" -> {
                        val event = if (statementSql.startsWith("SELECT GET_LOCK")) {
                            "statement.close:acquire"
                        } else if (statementSql.startsWith("SELECT RELEASE_LOCK")) {
                            "statement.close:release"
                        } else {
                            "statement.close:update"
                        }
                        events += event
                        if (statementSql.startsWith("UPDATE system_migration_version")) {
                            updateStatementCloseFailure?.let { throw it }
                        }
                        null
                    }
                    "equals" -> false
                    "hashCode" -> System.identityHashCode(call)
                    "toString" -> "PreparedStatement($statementSql)"
                    else -> defaultValue(methodReturnType(method))
                }
            }
        }

        private fun resultSet(value: Int?): ResultSet {
            var rowReturned = false
            var wasNull = false
            val metadata = proxy(ResultSetMetaData::class.java) { method, _ ->
                if (method == "getColumnCount") 1 else null
            }
            return proxy(ResultSet::class.java) { method, args ->
                when (method) {
                    "getMetaData" -> metadata
                    "getObject" -> value
                    "next" -> if (!rowReturned) {
                        rowReturned = true
                        true
                    } else {
                        false
                    }
                    "getInt" -> {
                        wasNull = value == null
                        value ?: 0
                    }
                    "wasNull" -> wasNull
                    "close" -> {
                        events += "resultSet.close"
                        null
                    }
                    "equals" -> false
                    "hashCode" -> System.identityHashCode(value)
                    "toString" -> "MigrationFailureStatusTestResultSet"
                    else -> defaultValue(methodReturnType(method))
                }
            }
        }
    }

    private companion object {
        fun methodReturnType(method: String): Class<*> = when (method) {
            "close", "setObject", "setNull" -> Void.TYPE
            "isClosed", "wasNull", "next", "equals" -> Boolean::class.javaPrimitiveType!!
            "hashCode", "getInt" -> Int::class.javaPrimitiveType!!
            "executeUpdate" -> Int::class.javaPrimitiveType!!
            else -> Any::class.java
        }

        fun <T> proxy(type: Class<T>, call: (String, Array<out Any?>?) -> Any?): T {
            val handler = InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "Proxy(${type.simpleName})"
                    else -> call(method.name, args)
                }
            }
            return type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
        }

        fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }
}
