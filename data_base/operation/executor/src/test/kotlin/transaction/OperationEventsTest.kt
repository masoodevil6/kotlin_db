package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base.core.events.DatabaseEventFailure
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEventType
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.events.OperationEvent
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_operation.executor.events.OperationEventType
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationEventsTest {
    @Test
    fun `public watch adapter maps legacy operation events without exposing the Throwable`() {
        var observed: OperationDatabaseEvent? = null
        val listener = OperationEventListener.fromDatabaseEventListener { event ->
            observed = event as OperationDatabaseEvent
        }
        val failure = SQLException("sensitive message")

        listener.onEvent(
            OperationEvent(
                type = OperationEventType.SQL_FAILED,
                sql = "SELECT ?",
                error = failure,
            ),
        )

        val event = requireNotNull(observed)
        assertEquals(OperationDatabaseEventType.SQL_FAILED, event.type)
        assertEquals("SELECT ?", event.sql)
        assertEquals(SQLException::class.qualifiedName, event.failure?.typeName)
        assertEquals(DatabaseEventFailure.Category.EXCEPTION, event.failure?.category)
    }

    @Test
    fun `domain event published by an active scope reaches the same watch listener`() {
        val fixture = JdbcFixture()
        val observed = mutableListOf<DatabaseEvent>()
        val domainEvent = object : DatabaseEvent {}
        val executor = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener.fromDatabaseEventListener { event ->
                observed += event
            },
        )

        val result = executor.connection { scope ->
            scope.publishDatabaseEvent(domainEvent)
            ExecuteResult.Success(true)
        }

        assertIs<ExecuteResult.Success<Boolean>>(result)
        val domainEvents = observed.filter { it === domainEvent }
        assertEquals(listOf<DatabaseEvent>(domainEvent), domainEvents)
    }

    @Test
    fun `typed select dml and ddl events surround JDBC execution and omit binding values`() {
        val fixture = JdbcFixture()
        val received = mutableListOf<OperationEvent>()
        val executor = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener {
                received += it
                fixture.timeline += "event:${it.type}"
            },
        )

        val outcome = executor.connection { scope ->
            assertEquals(
                ExecuteResult.Success("read"),
                scope.executeSelect(
                    BuiltQuery(
                        "SELECT :secret AS value",
                        mutableListOf(QueryParameterBinding("secret", "do-not-log", Types.VARCHAR)),
                    ),
                ) { rows ->
                    assertTrue(rows.next())
                    assertEquals(7, rows.getInt("value"))
                    "read"
                },
            )
            assertEquals(
                ExecuteResult.Success(41L),
                scope.executeInsert(
                    BoundQuery(
                        "INSERT INTO sample (value) VALUES (?)",
                        listOf(QueryParameterBinding("secret", "do-not-log", Types.VARCHAR)),
                    ),
                ),
            )
            assertEquals(
                ExecuteResult.Success(1),
                scope.executeUpdate(BuiltQuery("UPDATE sample SET value = :value", mutableListOf(
                    QueryParameterBinding("value", 8, Types.INTEGER),
                ))),
            )
            assertEquals(
                ExecuteResult.Success(1),
                scope.executeDelete(
                    BoundQuery(
                        "DELETE FROM sample WHERE id = ?",
                        listOf(QueryParameterBinding("id", 1, Types.INTEGER)),
                    ),
                ),
            )
            assertEquals(
                DdlExecutionOutcome.Success(true),
                scope.executeDdlWithPolicy("ALTER TABLE sample ADD COLUMN note INT", emptyList()) {
                    error("the policy is not used for successful DDL")
                },
            )
            assertEquals(ExecuteResult.Success(true), scope.executeDdl("CREATE TABLE sample (id INT)"))
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(outcome)
        assertEquals(
            listOf(
                OperationEventType.CONNECTION_OPENED,
                OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED,
                OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED,
                OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED,
                OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED,
                OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED,
                OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED,
                OperationEventType.CONNECTION_CLOSED,
            ),
            received.map { it.type },
        )
        assertEquals(
            listOf(
                "SELECT ? AS value",
                "INSERT INTO sample (value) VALUES (?)",
                "UPDATE sample SET value = ?",
                "DELETE FROM sample WHERE id = ?",
                "ALTER TABLE sample ADD COLUMN note INT",
                "CREATE TABLE sample (id INT)",
            ),
            received.filter { it.type == OperationEventType.SQL_STARTED }.mapNotNull { it.sql },
        )
        assertTrue(received.none { it.sql.orEmpty().contains("do-not-log") })
        val started = fixture.timeline.indexOf("event:SQL_STARTED")
        val prepare = fixture.timeline.indexOf("jdbc:prepare:SELECT ? AS value")
        val execute = fixture.timeline.indexOf("jdbc:executeQuery")
        val succeeded = fixture.timeline.indexOf("event:SQL_SUCCEEDED")
        assertTrue(started < prepare && prepare < execute && execute < succeeded)
        assertEquals(1, fixture.connectionAcquisitionCount)
        assertTrue("jdbc:commit" !in fixture.timeline && "jdbc:rollback" !in fixture.timeline)
    }

    @Test
    fun `raw SELECT callback runs inside the Operation event and statement lifetime`() {
        val fixture = JdbcFixture()
        val received = mutableListOf<OperationEvent>()
        var callbackCount = 0
        var capturedRows: ResultSet? = null
        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener {
                received += it
                fixture.timeline += "event:${it.type}"
            },
        ).connection { scope ->
            val rawResult = scope.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { result ->
                callbackCount++
                val rows = assertIs<ExecuteResult.Success<ResultSet>>(result).result!!
                capturedRows = rows
                assertTrue(rows.next())
                assertEquals(7, rows.getInt("value"))
                fixture.timeline += "callback"
            }
            assertEquals(ExecuteResult.Success(true), rawResult)
            assertTrue(requireNotNull(capturedRows).isClosed)
            ExecuteResult.Success(Unit)
        }

        assertEquals(ExecuteResult.Success(Unit), outcome)
        assertEquals(1, callbackCount)
        assertEquals(
            listOf(OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED),
            received.filter { it.type.name.startsWith("SQL_") }.map { it.type },
        )
        assertTrue(
            fixture.timeline.indexOf("event:SQL_STARTED") < fixture.timeline.indexOf("jdbc:executeQuery") &&
                fixture.timeline.indexOf("jdbc:executeQuery") < fixture.timeline.indexOf("callback") &&
                fixture.timeline.indexOf("callback") < fixture.timeline.indexOf("jdbc:statement.close") &&
                fixture.timeline.indexOf("jdbc:statement.close") < fixture.timeline.indexOf("event:SQL_SUCCEEDED"),
        )
        assertEquals(1, fixture.connectionAcquisitionCount)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw BoundQuery SELECT binds typed parameters and emits one event pair around callback and cleanup`() {
        val fixture = JdbcFixture()
        val received = mutableListOf<OperationEvent>()
        var callbackCount = 0
        var capturedRows: ResultSet? = null
        val boundQuery = BoundQuery(
            "SELECT ? AS value",
            listOf(QueryParameterBinding("value", "bound-value", Types.VARCHAR)),
        )

        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener {
                received += it
                fixture.timeline += "event:${it.type}"
            },
        ).connection { scope ->
            val rawResult = scope.executeRawSelect(boundQuery) { result ->
                callbackCount++
                val rows = assertIs<ExecuteResult.Success<ResultSet>>(result).result!!
                capturedRows = rows
                assertTrue(rows.next())
                assertEquals(7, rows.getInt("value"))
                fixture.timeline += "callback"
                assertEquals(0, fixture.closedCount)
            }
            assertEquals(ExecuteResult.Success(true), rawResult)
            assertTrue(requireNotNull(capturedRows).isClosed)
            assertEquals(0, fixture.closedCount)
            ExecuteResult.Success(Unit)
        }

        assertEquals(ExecuteResult.Success(Unit), outcome)
        assertEquals(1, callbackCount)
        assertTrue(requireNotNull(capturedRows).isClosed)
        assertEquals(
            listOf(listOf<Any?>(1, "bound-value", Types.VARCHAR)),
            fixture.bindingCalls,
        )
        assertEquals(
            listOf(OperationEventType.SQL_STARTED, OperationEventType.SQL_SUCCEEDED),
            received.filter { it.type.name.startsWith("SQL_") }.map { it.type },
        )
        assertEquals(1, received.count { it.type == OperationEventType.SQL_STARTED })
        assertEquals(1, received.count { it.type == OperationEventType.SQL_SUCCEEDED })
        assertEquals(
            listOf("SELECT ? AS value"),
            received.filter { it.type == OperationEventType.SQL_STARTED }.mapNotNull { it.sql },
        )
        assertTrue(
            fixture.timeline.indexOf("event:SQL_STARTED") < fixture.timeline.indexOf("jdbc:prepare:SELECT ? AS value") &&
                fixture.timeline.indexOf("jdbc:executeQuery") < fixture.timeline.indexOf("callback") &&
                fixture.timeline.indexOf("callback") < fixture.timeline.indexOf("jdbc:statement.close") &&
                fixture.timeline.indexOf("jdbc:statement.close") < fixture.timeline.indexOf("event:SQL_SUCCEEDED") &&
                fixture.timeline.indexOf("event:SQL_SUCCEEDED") < fixture.timeline.indexOf("jdbc:close"),
        )
        assertEquals(1, fixture.connectionAcquisitionCount)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw BoundQuery SELECT failure emits one SQL_FAILED and delivers failure once`() {
        val sqlFailure = SQLException("bound raw select failed")
        val fixture = JdbcFixture(failures = mapOf("executeQuery" to sqlFailure))
        val received = mutableListOf<OperationEvent>()
        var callbackCount = 0

        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener {
                received += it
                if (it.type == OperationEventType.SQL_FAILED) {
                    fixture.timeline += "event:SQL_FAILED"
                }
            },
        ).connection { scope ->
            val result = scope.executeRawSelect(
                BoundQuery("SELECT ? AS value", listOf(QueryParameterBinding("value", 9, Types.INTEGER))),
            ) { delivered ->
                callbackCount++
                assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(delivered).exception)
            }
            assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(result).exception)
            ExecuteResult.Success(Unit)
        }

        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(outcome).exception)
        assertEquals(1, callbackCount)
        assertEquals(
            listOf(OperationEventType.SQL_STARTED, OperationEventType.SQL_FAILED),
            received.filter { it.type.name.startsWith("SQL_") }.map { it.type },
        )
        assertSame(sqlFailure, received.single { it.type == OperationEventType.SQL_FAILED }.error)
        assertEquals("SELECT ? AS value", received.single { it.type == OperationEventType.SQL_FAILED }.sql)
        assertTrue(fixture.timeline.indexOf("event:SQL_STARTED") < fixture.timeline.indexOf("jdbc:prepare:SELECT ? AS value"))
        assertTrue(fixture.timeline.indexOf("jdbc:statement.close") < fixture.timeline.indexOf("event:SQL_FAILED"))
        assertEquals(1, fixture.connectionAcquisitionCount)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw SELECT SQL failure is delivered once and emits SQL_FAILED`() {
        val sqlFailure = SQLException("raw select failed")
        val fixture = JdbcFixture(failures = mapOf("executeQuery" to sqlFailure))
        val received = mutableListOf<OperationEvent>()
        var callbackCount = 0
        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            val result = scope.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { selected ->
                callbackCount++
                assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(selected).exception)
            }
            assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(result).exception)
            ExecuteResult.Success(Unit)
        }

        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(outcome).exception)
        assertEquals(1, callbackCount)
        assertEquals(
            listOf(OperationEventType.SQL_STARTED, OperationEventType.SQL_FAILED),
            received.filter { it.type.name.startsWith("SQL_") }.map { it.type },
        )
        assertSame(sqlFailure, received.last { it.type == OperationEventType.SQL_FAILED }.error)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw SELECT callback failure remains primary with statement cleanup suppressed`() {
        val callbackFailure = IllegalStateException("raw callback failed")
        val closeFailure = SQLException("statement close failed")
        val fixture = JdbcFixture(failures = mapOf("statement.close" to closeFailure))
        val received = mutableListOf<OperationEvent>()
        var callbackCount = 0
        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            scope.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) {
                callbackCount++
                throw callbackFailure
            }
            ExecuteResult.Success(Unit)
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(outcome).exception)
        assertEquals(listOf(closeFailure), callbackFailure.suppressed.toList())
        assertEquals(1, callbackCount)
        assertSame(callbackFailure, received.last { it.type == OperationEventType.SQL_FAILED }.error)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw SELECT callback Error remains fatal and resources still close`() {
        val fatal = AssertionError("raw callback Error")
        val fixture = JdbcFixture()
        val received = mutableListOf<OperationEvent>()
        var scope: OperationExecutionScope? = null

        val thrown = kotlin.test.assertFailsWith<AssertionError> {
            OperationExecutionExecutor(
                { fixture.acquireConnection() },
                OperationEventListener { received += it },
            ).connection { activeScope ->
                scope = activeScope
                activeScope.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) {
                    throw fatal
                }
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, requireNotNull(scope).fatalError)
        assertSame(fatal, received.last { it.type == OperationEventType.SQL_FAILED }.error)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw SELECT failure remains primary when statement cleanup also fails`() {
        val sqlFailure = SQLException("raw SQL failure")
        val closeFailure = SQLException("statement close failure")
        val fixture = JdbcFixture(
            failures = mapOf("executeQuery" to sqlFailure, "statement.close" to closeFailure),
        )
        val received = mutableListOf<OperationEvent>()
        var callbackCount = 0
        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            scope.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { selected ->
                callbackCount++
                assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(selected).exception)
            }
            ExecuteResult.Success(Unit)
        }

        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(outcome).exception)
        assertEquals(listOf(closeFailure), sqlFailure.suppressed.toList())
        assertEquals(1, callbackCount)
        assertSame(sqlFailure, received.last { it.type == OperationEventType.SQL_FAILED }.error)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw SELECT cleanup failure after callback success fails the Operation without redelivery`() {
        val closeFailure = SQLException("statement close failed")
        val fixture = JdbcFixture(failures = mapOf("statement.close" to closeFailure))
        val received = mutableListOf<OperationEvent>()
        var callbackCount = 0
        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            scope.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { result ->
                callbackCount++
                assertIs<ExecuteResult.Success<ResultSet>>(result)
            }
            ExecuteResult.Success(Unit)
        }

        assertSame(closeFailure, assertIs<ExecuteResult.Failure>(outcome).exception)
        assertEquals(1, callbackCount)
        assertSame(closeFailure, received.last { it.type == OperationEventType.SQL_FAILED }.error)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `raw SELECT in child transaction emits one pair and reuses parent connection`() {
        val fixture = JdbcFixture()
        val received = mutableListOf<OperationEvent>()
        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { parent ->
            val parentRead = parent.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { result ->
                assertIs<ExecuteResult.Success<ResultSet>>(result)
            }
            assertEquals(ExecuteResult.Success(true), parentRead)
            parent.transaction { child ->
                child.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { result ->
                    assertIs<ExecuteResult.Success<ResultSet>>(result)
                }
            }
        }

        assertEquals(ExecuteResult.Success(true), outcome)
        assertEquals(1, fixture.connectionAcquisitionCount)
        assertEquals(1, fixture.closedCount)
        assertEquals(
            2,
            received.count { it.type == OperationEventType.SQL_STARTED },
        )
        assertEquals(
            2,
            received.count { it.type == OperationEventType.SQL_SUCCEEDED },
        )
    }

    @Test
    fun `SQL failure publishes failed event with original cause`() {
        val sqlFailure = SQLException("query failed")
        val fixture = JdbcFixture(failures = mapOf("executeUpdate" to sqlFailure))
        val received = mutableListOf<OperationEvent>()
        val outcome = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            scope.executeUpdate(
                BoundQuery(
                    "UPDATE sample SET value = ?",
                    listOf(QueryParameterBinding("value", 1, Types.INTEGER)),
                ),
            )
        }

        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(outcome).exception)
        val sqlEvents = received.filter { it.type.name.startsWith("SQL_") }
        assertEquals(listOf(OperationEventType.SQL_STARTED, OperationEventType.SQL_FAILED), sqlEvents.map { it.type })
        assertSame(sqlFailure, sqlEvents.last().error)
        assertEquals("UPDATE sample SET value = ?", sqlEvents.last().sql)
    }

    @Test
    fun `DDL failure is reported before failure policy handles it`() {
        val sqlFailure = SQLException("DDL failed")
        val fixture = JdbcFixture(failures = mapOf("execute" to sqlFailure))
        val received = mutableListOf<OperationEvent>()
        val result = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            val outcome = scope.executeDdlWithPolicy("ALTER TABLE sample DROP COLUMN value", emptyList()) {
                DdlFailureDisposition.RECOVERABLE
            }
            assertSame(sqlFailure, assertIs<DdlExecutionOutcome.Failure>(outcome).exception)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        val sqlEvents = received.filter { it.type.name.startsWith("SQL_") }
        assertEquals(listOf(OperationEventType.SQL_STARTED, OperationEventType.SQL_FAILED), sqlEvents.map { it.type })
        assertEquals("ALTER TABLE sample DROP COLUMN value", sqlEvents.last().sql)
        assertSame(sqlFailure, sqlEvents.last().error)
    }

    @Test
    fun `SQL failure remains primary when statement cleanup also fails`() {
        val sqlFailure = SQLException("delete failed")
        val statementCloseFailure = SQLException("statement close failed")
        val fixture = JdbcFixture(
            failures = mapOf("executeUpdate" to sqlFailure, "statement.close" to statementCloseFailure),
        )
        val received = mutableListOf<OperationEvent>()
        val result = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            scope.executeDelete(BoundQuery("DELETE FROM sample", emptyList()))
        }

        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf(statementCloseFailure), sqlFailure.suppressed.toList())
        assertSame(sqlFailure, received.last { it.type == OperationEventType.SQL_FAILED }.error)
    }

    @Test
    fun `select callback failure and Error remain primary and are observed`() {
        val callbackFailure = IllegalStateException("mapping failed")
        val fixture = JdbcFixture()
        val received = mutableListOf<OperationEvent>()
        val failed = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            scope.executeSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) {
                throw callbackFailure
            }
        }
        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(failed).exception)
        assertSame(callbackFailure, received.last { it.type == OperationEventType.SQL_FAILED }.error)

        val fatal = AssertionError("fatal mapper error")
        val fatalFixture = JdbcFixture()
        val fatalEvents = mutableListOf<OperationEvent>()
        var fatalScope: OperationExecutionScope? = null
        val thrown = kotlin.test.assertFailsWith<AssertionError> {
            OperationExecutionExecutor(
                { fatalFixture.acquireConnection() },
                OperationEventListener { fatalEvents += it },
            ).connection { scope ->
                fatalScope = scope
                scope.executeSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { throw fatal }
            }
        }
        assertSame(fatal, thrown)
        assertSame(fatal, requireNotNull(fatalScope).fatalError)
        assertSame(fatal, fatalEvents.last { it.type == OperationEventType.SQL_FAILED }.error)
        assertEquals(1, fatalFixture.closedCount)
    }

    @Test
    fun `observer throwables do not change success failure or primary errors`() {
        val listener = OperationEventListener { throw AssertionError("observer failed") }

        val successfulFixture = JdbcFixture()
        val success = OperationExecutionExecutor({ successfulFixture.acquireConnection() }, listener).connection { scope ->
            scope.executeDelete(BoundQuery("DELETE FROM sample", emptyList()))
        }
        assertEquals(ExecuteResult.Success(1), success)

        val primary = SQLException("database failure")
        val failedFixture = JdbcFixture(failures = mapOf("executeUpdate" to primary))
        val failure = OperationExecutionExecutor({ failedFixture.acquireConnection() }, listener).connection { scope ->
            scope.executeDelete(BoundQuery("DELETE FROM sample", emptyList()))
        }
        assertSame(primary, assertIs<ExecuteResult.Failure>(failure).exception)
        assertTrue(primary.suppressed.isEmpty())

        val rawFixture = JdbcFixture()
        val attemptedRawEvents = mutableListOf<OperationEventType>()
        val rawListener = OperationEventListener {
            attemptedRawEvents += it.type
            throw AssertionError("raw SELECT observer failed")
        }
        val rawOutcome = OperationExecutionExecutor({ rawFixture.acquireConnection() }, rawListener).connection { scope ->
            assertEquals(
                ExecuteResult.Success(true),
                scope.executeRawSelect(BuiltQuery("SELECT 1 AS value", mutableListOf())) { delivered ->
                    assertIs<ExecuteResult.Success<ResultSet>>(delivered)
                },
            )
            ExecuteResult.Success(Unit)
        }
        assertEquals(ExecuteResult.Success(Unit), rawOutcome)
        assertEquals(
            listOf(
                OperationEventType.CONNECTION_OPENED,
                OperationEventType.SQL_STARTED,
                OperationEventType.SQL_SUCCEEDED,
                OperationEventType.CONNECTION_CLOSED,
            ),
            attemptedRawEvents,
        )
    }

    @Test
    fun `connection open and close failures are observed without replacing operation failure`() {
        val openFailure = SQLException("open failed")
        val openEvents = mutableListOf<OperationEvent>()
        val openResult = OperationExecutionExecutor(
            { throw openFailure },
            OperationEventListener { openEvents += it },
        ).connection { ExecuteResult.Success(Unit) }
        assertSame(openFailure, assertIs<ExecuteResult.Failure>(openResult).exception)
        assertEquals(OperationEventType.CONNECTION_OPEN_FAILED, openEvents.single().type)
        assertSame(openFailure, openEvents.single().error)

        val primary = SQLException("callback failed")
        val closeFailure = SQLException("close failed")
        val closeEvents = mutableListOf<OperationEvent>()
        val fixture = JdbcFixture(failures = mapOf("close" to closeFailure))
        val closeResult = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { closeEvents += it },
        ).connection { ExecuteResult.Failure(primary) }

        assertSame(primary, assertIs<ExecuteResult.Failure>(closeResult).exception)
        assertEquals(listOf(OperationEventType.CONNECTION_OPENED, OperationEventType.CONNECTION_CLOSE_FAILED), closeEvents.map { it.type })
        assertSame(closeFailure, closeEvents.last().error)
        assertEquals(listOf(closeFailure), primary.suppressed.toList())
    }

    @Test
    fun `root and child transaction lifecycle events reflect actual JDBC calls`() {
        val fixture = JdbcFixture()
        val received = mutableListOf<OperationEvent>()
        val connectionResult = OperationExecutionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).connection { scope ->
            scope.transaction { child ->
                child.executeDelete(BoundQuery("DELETE FROM sample", emptyList()))
            }
        }

        assertEquals(ExecuteResult.Success(1), connectionResult)
        assertEquals(
            listOf(
                OperationEventType.CONNECTION_OPENED,
                OperationEventType.TRANSACTION_STARTED,
                OperationEventType.SQL_STARTED,
                OperationEventType.SQL_SUCCEEDED,
                OperationEventType.TRANSACTION_COMMITTED,
                OperationEventType.CONNECTION_CLOSED,
            ),
            received.map { it.type },
        )
        assertEquals(1, fixture.connectionAcquisitionCount)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `transaction failure reports rollback and lifecycle failures`() {
        val sqlFailure = SQLException("delete failed")
        val rollbackFailure = SQLException("rollback failed")
        val closeFailure = SQLException("close failed")
        val fixture = JdbcFixture(
            failures = mapOf(
                "executeUpdate" to sqlFailure,
                "rollback" to rollbackFailure,
                "close" to closeFailure,
            ),
        )
        val received = mutableListOf<OperationEvent>()
        val result = OperationTransactionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).transaction { scope ->
            scope.executeDelete(BoundQuery("DELETE FROM sample", emptyList()))
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(
            listOf(
                OperationEventType.CONNECTION_OPENED,
                OperationEventType.TRANSACTION_STARTED,
                OperationEventType.SQL_STARTED,
                OperationEventType.SQL_FAILED,
                OperationEventType.TRANSACTION_ROLLBACK_FAILED,
                OperationEventType.CONNECTION_CLOSE_FAILED,
            ),
            received.map { it.type },
        )
        assertSame(sqlFailure, received.first { it.type == OperationEventType.SQL_FAILED }.error)
        assertSame(rollbackFailure, received.first { it.type == OperationEventType.TRANSACTION_ROLLBACK_FAILED }.error)
        assertSame(closeFailure, received.first { it.type == OperationEventType.CONNECTION_CLOSE_FAILED }.error)
        assertEquals(1, fixture.connectionAcquisitionCount)
        assertEquals(1, fixture.closedCount)
    }

    @Test
    fun `commit and rollback success and failure events match JDBC outcomes`() {
        val committedEvents = mutableListOf<OperationEvent>()
        val committed = JdbcFixture()
        val commitResult = OperationTransactionExecutor(
            { committed.acquireConnection() },
            OperationEventListener { committedEvents += it },
        ).transaction { ExecuteResult.Success(Unit) }
        assertIs<ExecuteResult.Success<Unit>>(commitResult)
        assertTrue(committedEvents.any { it.type == OperationEventType.TRANSACTION_COMMITTED })

        val commitFailure = SQLException("commit failed")
        val rollbackEvents = mutableListOf<OperationEvent>()
        val failedCommit = JdbcFixture(failures = mapOf("commit" to commitFailure))
        val result = OperationTransactionExecutor(
            { failedCommit.acquireConnection() },
            OperationEventListener { rollbackEvents += it },
        ).transaction { ExecuteResult.Success(Unit) }
        assertIs<ExecuteResult.Failure>(result)
        assertEquals(
            listOf(
                OperationEventType.CONNECTION_OPENED,
                OperationEventType.TRANSACTION_STARTED,
                OperationEventType.TRANSACTION_COMMIT_FAILED,
                OperationEventType.TRANSACTION_ROLLED_BACK,
                OperationEventType.CONNECTION_CLOSED,
            ),
            rollbackEvents.map { it.type },
        )
        assertSame(commitFailure, rollbackEvents.first { it.type == OperationEventType.TRANSACTION_COMMIT_FAILED }.error)
    }

    @Test
    fun `transaction start failure is reported and connection still closes`() {
        val startFailure = SQLException("autoCommit change failed")
        val fixture = JdbcFixture(failures = mapOf("setAutoCommit" to startFailure))
        val received = mutableListOf<OperationEvent>()
        val result = OperationTransactionExecutor(
            { fixture.acquireConnection() },
            OperationEventListener { received += it },
        ).transaction { ExecuteResult.Success(Unit) }

        assertSame(startFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(
            listOf(OperationEventType.CONNECTION_OPENED, OperationEventType.TRANSACTION_START_FAILED, OperationEventType.CONNECTION_CLOSED),
            received.map { it.type },
        )
        assertSame(startFailure, received[1].error)
        assertEquals(1, fixture.closedCount)
    }

    private class JdbcFixture(
        private val failures: Map<String, Throwable> = emptyMap(),
    ) {
        val timeline = mutableListOf<String>()
        var connectionAcquisitionCount: Int = 0
            private set
        var closedCount: Int = 0
            private set
        val bindingCalls = mutableListOf<List<Any?>>()
        private var autoCommit = true

        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args?.firstOrNull() as String
                    timeline += "jdbc:prepare:$sql"
                    statement(sql)
                }
                "setAutoCommit" -> {
                    timeline += "jdbc:setAutoCommit:${args?.firstOrNull()}"
                    failures[method.name]?.let { throw it }
                    autoCommit = args?.firstOrNull() as Boolean
                    null
                }
                "getAutoCommit" -> autoCommit
                "commit", "rollback" -> {
                    timeline += "jdbc:${method.name}"
                    failures[method.name]?.let { throw it }
                    null
                }
                "close" -> {
                    timeline += "jdbc:close"
                    closedCount++
                    failures[method.name]?.let { throw it }
                    null
                }
                "isClosed" -> closedCount > 0
                "toString" -> "OperationEventsTestConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        } as Connection

        fun acquireConnection(): Connection {
            connectionAcquisitionCount++
            timeline += "jdbc:acquire"
            return connection
        }

        private fun statement(sql: String): PreparedStatement {
            val resultSets = mutableListOf<ResultSet>()
            return Proxy.newProxyInstance(
                PreparedStatement::class.java.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "executeQuery" -> {
                        timeline += "jdbc:executeQuery"
                        failures[method.name]?.let { throw it }
                        resultSet(listOf(linkedMapOf("value" to 7))).also(resultSets::add)
                    }
                    "executeUpdate" -> {
                        timeline += "jdbc:executeUpdate"
                        failures[method.name]?.let { throw it }
                        1
                    }
                    "execute" -> {
                        timeline += "jdbc:execute"
                        failures[method.name]?.let { throw it }
                        false
                    }
                    "getGeneratedKeys" -> resultSet(listOf(linkedMapOf("key" to 41L))).also(resultSets::add)
                    "close" -> {
                        timeline += "jdbc:statement.close"
                        resultSets.filterNot { it.isClosed }.forEach { it.close() }
                        failures["statement.close"]?.let { throw it }
                        null
                    }
                    "setObject", "setNull" -> {
                        bindingCalls += args.orEmpty().toList()
                        null
                    }
                    "isClosed" -> false
                    "toString" -> "PreparedStatement($sql)"
                    else -> defaultValue(method.returnType)
                }
            } as PreparedStatement
        }

        private fun resultSet(rows: List<LinkedHashMap<String, Any?>>): ResultSet {
            var rowIndex = -1
            var lastWasNull = false
            var closed = false
            val columns = rows.firstOrNull()?.keys?.toList().orEmpty()
            val metadata = Proxy.newProxyInstance(
                ResultSetMetaData::class.java.classLoader,
                arrayOf(ResultSetMetaData::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getColumnCount" -> columns.size
                    "getColumnLabel", "getColumnName" -> columns[(args?.firstOrNull() as Int) - 1]
                    else -> defaultValue(method.returnType)
                }
            } as ResultSetMetaData

            return Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getMetaData" -> metadata
                    "next" -> {
                        rowIndex++
                        rowIndex < rows.size
                    }
                    "getInt" -> value(rows, columns, rowIndex, args).also { lastWasNull = it == null }?.toString()?.toInt() ?: 0
                    "getLong" -> value(rows, columns, rowIndex, args).also { lastWasNull = it == null }?.toString()?.toLong() ?: 0L
                    "getString" -> value(rows, columns, rowIndex, args).also { lastWasNull = it == null }?.toString()
                    "wasNull" -> lastWasNull
                    "close" -> {
                        if (!closed) timeline += "jdbc:resultSet.close"
                        closed = true
                        null
                    }
                    "isClosed" -> closed
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }

        private fun value(
            rows: List<LinkedHashMap<String, Any?>>,
            columns: List<String>,
            rowIndex: Int,
            args: Array<out Any?>?,
        ): Any? {
            if (rowIndex !in rows.indices) return null
            val key = when (val selector = args?.firstOrNull()) {
                is Int -> columns[selector - 1]
                is String -> selector
                else -> error("A column selector is required")
            }
            return rows[rowIndex][key]
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
