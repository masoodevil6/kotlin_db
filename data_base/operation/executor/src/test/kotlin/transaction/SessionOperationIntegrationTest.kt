package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.then.OperationPipelineStageContext
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.api.result.SessionExecutionResult
import gog.my_project.data_base_session.api.interfaces.api.ISessionCommandApi
import gog.my_project.data_base_session.api.interfaces.executor.ISessionCommandExecutor
import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.acquire.IAcquireSessionAdvisoryLockApi
import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.release.IReleaseSessionAdvisoryLockApi
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.acquire.AcquireSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.metadata.advisory_lock.release.ReleaseSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.ast.value.SessionAdvisoryLockNameGenerator
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock
import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_operation.executor.session.result.SessionConsumer
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionOperationIntegrationTest {
    @Test
    fun `session command adapter delegates typed commands on its borrowed connection for each dialect`() {
        for (dialect in listOf(SessionDialect.MYSQL, SessionDialect.MARIA_DB)) {
            val script = SessionJdbcScript(listOf(1, 1))
            val adapter = OperationSessionCommandExecutor(script.connection, dialect)
            val acquireAst = acquireAst()
            val releaseAst = releaseAst()

            val acquired: AcquireSessionLockResult = adapter.acquire(acquireAst)
            val released: ReleaseSessionLockResult = adapter.release(releaseAst)

            assertSame(AcquireSessionLockResult.Acquired, acquired)
            assertSame(ReleaseSessionLockResult.Released, released)
            assertEquals(
                listOf(
                    "SELECT GET_LOCK(?, ?) AS acquired",
                    "SELECT RELEASE_LOCK(?) AS released",
                ),
                script.sql,
            )
            assertEquals(
                listOf(
                    Binding(1, "SELECT GET_LOCK(?, ?) AS acquired", lockName(), Types.VARCHAR),
                    Binding(2, "SELECT GET_LOCK(?, ?) AS acquired", 0, Types.INTEGER),
                    Binding(1, "SELECT RELEASE_LOCK(?) AS released", lockName(), Types.VARCHAR),
                ),
                script.bindings,
            )
            assertEquals(2, script.prepareCount)
            assertEquals(2, script.statementCloseCount)
            assertEquals(2, script.resultSetCloseCount)
            assertEquals(0, script.connectionCloseCount)
            assertEquals(0, script.commitCount)
            assertEquals(0, script.rollbackCount)
            assertEquals(0, script.autoCommitChangeCount)
        }
    }

    @Test
    fun `session command adapter propagates errors unchanged and leaves connection lifecycle alone`() {
        val fatal = AssertionError("session adapter execution failed")
        val script = SessionJdbcScript(statuses = listOf(1), executeError = fatal)
        val adapter = OperationSessionCommandExecutor(script.connection, SessionDialect.MYSQL)

        val thrown = assertFailsWith<AssertionError> {
            adapter.acquire(acquireAst())
        }

        assertSame(fatal, thrown)
        assertEquals(1, script.prepareCount)
        assertEquals(1, script.statementCloseCount)
        assertEquals(0, script.connectionCloseCount)
        assertEquals(0, script.commitCount)
        assertEquals(0, script.rollbackCount)
        assertEquals(0, script.autoCommitChangeCount)
    }

    @Test
    fun `session command adapter propagates release errors unchanged`() {
        val fatal = AssertionError("session adapter release failed")
        val script = SessionJdbcScript(statuses = listOf(1), executeError = fatal)
        val adapter = OperationSessionCommandExecutor(script.connection, SessionDialect.MARIA_DB)

        val thrown = assertFailsWith<AssertionError> {
            adapter.release(releaseAst())
        }

        assertSame(fatal, thrown)
        assertEquals(1, script.prepareCount)
        assertEquals(1, script.statementCloseCount)
        assertEquals(0, script.connectionCloseCount)
        assertEquals(0, script.commitCount)
        assertEquals(0, script.rollbackCount)
        assertEquals(0, script.autoCommitChangeCount)
    }

    @Test
    fun `generic session entry point dispatches acquire and release with inferred result types`() {
        val script = SessionJdbcScript(listOf(1, 1))
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val acquireConsumer = scope.session(acquireApi())
            val releaseConsumer = scope.session(releaseApi())

            assertEquals(0, script.prepareCount)
            val acquireResult: ExecuteResult<AcquireSessionLockResult> = acquireConsumer.execute()
            val releaseResult: ExecuteResult<ReleaseSessionLockResult> = releaseConsumer.execute()

            assertSame(
                AcquireSessionLockResult.Acquired,
                assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(acquireResult).result,
            )
            assertSame(
                ReleaseSessionLockResult.Released,
                assertIs<ExecuteResult.Success<ReleaseSessionLockResult>>(releaseResult).result,
            )
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        assertEquals(2, script.prepareCount)
        assertEquals(2, script.statementCloseCount)
        assertEquals(2, script.resultSetCloseCount)
        assertEquals(1, script.connectionCloseCount)
        assertEquals(0, script.commitCount)
        assertEquals(0, script.rollbackCount)
    }

    @Test
    fun `generic session fluent API keeps Acquire and Release semantic callback types`() {
        val script = SessionJdbcScript(listOf(1, 1))
        var acquireValue: AcquireSessionLockResult? = null
        var releaseValue: ReleaseSessionLockResult? = null
        var acquireFailure: Throwable? = null
        var releaseFailure: Throwable? = null

        val operationResult = OperationExecutionExecutor { script.connection }.connection { ctx ->
            val acquireChain: ExecuteResult<Unit> = verifyAcquireFluentApi(
                ctx = ctx,
                onSuccess = { acquireValue = it },
                onError = { acquireFailure = it },
            )
            val releaseChain: ExecuteResult<Unit> = verifyReleaseFluentApi(
                ctx = ctx,
                onSuccess = { releaseValue = it },
                onError = { releaseFailure = it },
            )

            assertEquals(Unit, assertIs<ExecuteResult.Success<Unit>>(acquireChain).result)
            assertEquals(Unit, assertIs<ExecuteResult.Success<Unit>>(releaseChain).result)
            assertNull(acquireFailure)
            assertNull(releaseFailure)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(operationResult)
        assertSame(AcquireSessionLockResult.Acquired, acquireValue)
        assertSame(ReleaseSessionLockResult.Released, releaseValue)
        assertEquals(2, script.prepareCount)
    }

    @Test
    fun `generic session fluent error receives the original failure and preserves the chain`() {
        val sqlFailure = SQLException("fluent session acquire failed")
        val script = SessionJdbcScript(statuses = emptyList(), prepareFailure = sqlFailure)
        var observedFailure: Throwable? = null

        val operationResult = OperationExecutionExecutor { script.connection }.connection { ctx ->
            val execution: ExecuteResult<AcquireSessionLockResult> = ctx.session(
                acquireSessionAdvisoryLock {
                    resource("application", "cache-refresh")
                    noWait()
                },
            ).execute()
            val originalFailure = assertIs<ExecuteResult.Failure>(execution)
            var successCalls = 0

            val chained: ExecuteResult<String> = execution
                .success { value: AcquireSessionLockResult ->
                    successCalls++
                    value.toString()
                }
                .error { failure: Throwable ->
                    observedFailure = failure
                    failure.message
                }

            assertEquals(0, successCalls)
            val failure = assertIs<ExecuteResult.Failure>(chained)
            assertSame(originalFailure, failure)
            assertSame(failure.exception, observedFailure)
            assertSame(sqlFailure, failure.exception.cause)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(operationResult)
        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(operationResult).exception.cause)
        assertEquals(1, script.prepareCount)
    }

    @Test
    fun `generic session consumer observes AST mutation before first execution`() {
        val script = SessionJdbcScript(listOf(1))
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val api = acquireApi()
            val consumer = scope.session(api)
            assertEquals(0, script.prepareCount)

            val updatedResource = LockResource("application", "changed-before-execute")
            api.ast.metadata.resource = updatedResource
            api.ast.metadata.waitPolicy = LockWaitPolicy.NoWait

            val execution: ExecuteResult<AcquireSessionLockResult> = consumer.execute()

            assertSame(
                AcquireSessionLockResult.Acquired,
                assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(execution).result,
            )
            assertEquals(SessionAdvisoryLockNameGenerator.generate(updatedResource), script.bindings[0].value)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        assertEquals(1, script.prepareCount)
    }

    @Test
    fun `generic session consumer is one shot and latches its second execution failure`() {
        val script = SessionJdbcScript(listOf(1, 1))
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val consumer = scope.session(acquireApi())
            val first: ExecuteResult<AcquireSessionLockResult> = consumer.execute()
            assertSame(
                AcquireSessionLockResult.Acquired,
                assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(first).result,
            )

            val second = assertIs<ExecuteResult.Failure>(consumer.execute())
            assertIs<IllegalStateException>(second.exception)
            assertSame(second.exception, scope.firstFailure)
            assertEquals(1, script.prepareCount)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, script.prepareCount)
        assertEquals(1, script.connectionCloseCount)
    }

    @Test
    fun `generic session consumer after scope expiration does not latch failure or execute JDBC`() {
        val script = SessionJdbcScript(emptyList())
        lateinit var scopeSeen: OperationExecutionScope
        lateinit var consumer: SessionConsumer<AcquireSessionLockResult>
        val operationResult = OperationExecutionExecutor { script.connection }.connection { scope ->
            scopeSeen = scope
            consumer = scope.session(acquireApi())
            assertEquals(0, script.prepareCount)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(operationResult)
        assertNull(scopeSeen.firstFailure)

        val expiredResult = assertIs<ExecuteResult.Failure>(consumer.execute())

        assertIs<IllegalStateException>(expiredResult.exception)
        assertNull(scopeSeen.firstFailure)
        assertEquals(0, script.prepareCount)
        assertEquals(1, script.connectionCloseCount)
    }

    @Test
    fun `generic session command returns prior scope failure without another JDBC call`() {
        val priorSqlFailure = SQLException("prior operation failed")
        val script = SessionJdbcScript(statuses = emptyList(), prepareFailure = priorSqlFailure)
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val prior = assertIs<ExecuteResult.Failure>(scope.executeDdl("CREATE TABLE prior_failure (id INT)"))
            val session = assertIs<ExecuteResult.Failure>(scope.session(acquireApi()).execute())

            assertSame(prior.exception, session.exception)
            assertSame(priorSqlFailure, session.exception)
            assertEquals(1, script.prepareCount)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertSame(priorSqlFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, script.prepareCount)
    }

    @Test
    fun `generic session execution converts exceptions and rethrows recorded errors`() {
        val sqlFailure = SQLException("session prepare failed")
        val sqlScript = SessionJdbcScript(statuses = emptyList(), prepareFailure = sqlFailure)
        val failureResult = OperationExecutionExecutor { sqlScript.connection }.connection { scope ->
            val failure = assertIs<ExecuteResult.Failure>(scope.session(acquireApi()).execute())
            assertSame(failure.exception, scope.firstFailure)
            assertSame(sqlFailure, failure.exception.cause)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(failureResult)
        assertEquals(1, sqlScript.prepareCount)
        assertEquals(1, sqlScript.connectionCloseCount)

        val fatal = AssertionError("generic session execution failed fatally")
        val errorScript = SessionJdbcScript(statuses = listOf(1), executeError = fatal)
        lateinit var errorScope: OperationExecutionScope
        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { errorScript.connection }.connection { scope ->
                errorScope = scope
                scope.session(acquireApi()).execute()
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, errorScope.fatalError)
        assertEquals(1, errorScript.prepareCount)
        assertEquals(1, errorScript.connectionCloseCount)
    }

    @Test
    fun `generic session command is rejected from a non owner thread before JDBC`() {
        val script = SessionJdbcScript(emptyList())
        val operationResult = OperationExecutionExecutor { script.connection }.connection { scope ->
            val consumer = scope.session(acquireApi())
            var threadResult: ExecuteResult<AcquireSessionLockResult>? = null
            val worker = thread(start = true) {
                threadResult = consumer.execute()
            }
            worker.join()

            val failure = assertIs<ExecuteResult.Failure>(threadResult)
            assertIs<IllegalStateException>(failure.exception)
            assertSame(failure.exception, scope.firstFailure)
            assertEquals(0, script.prepareCount)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(operationResult)
        assertEquals(0, script.prepareCount)
    }

    @Test
    fun `default context methods fail closed and record failures`() {
        val failures = mutableListOf<Throwable>()
        val context = object : OperationExecutionContext {
            override fun executeDdl(
                sql: String,
                parameters: List<OperationParameter>,
            ): ExecuteResult<Boolean> = ExecuteResult.Success(true)

            override fun <T> failed(failure: Throwable): ExecuteResult<T> {
                failures += failure
                return ExecuteResult.Failure(failure)
            }
        }

        val acquire = assertIs<ExecuteResult.Failure>(context.acquireSessionAdvisoryLock(acquireAst()))
        val release = assertIs<ExecuteResult.Failure>(context.releaseSessionAdvisoryLock(releaseAst()))
        val typedAcquire = assertIs<ExecuteResult.Failure>(context.acquireSessionAdvisoryLock(acquireApi()))
        val typedRelease = assertIs<ExecuteResult.Failure>(context.releaseSessionAdvisoryLock(releaseApi()))
        val genericConsumer = context.session(acquireApi())
        assertEquals(4, failures.size)
        val genericAcquire = assertIs<ExecuteResult.Failure>(genericConsumer.execute())

        assertIs<UnsupportedOperationException>(acquire.exception)
        assertIs<UnsupportedOperationException>(release.exception)
        assertIs<UnsupportedOperationException>(typedAcquire.exception)
        assertIs<UnsupportedOperationException>(typedRelease.exception)
        assertIs<UnsupportedOperationException>(genericAcquire.exception)
        assertEquals(
            listOf(
                acquire.exception,
                release.exception,
                typedAcquire.exception,
                typedRelease.exception,
                genericAcquire.exception,
            ),
            failures,
        )
    }

    @Test
    fun `managed root connection executes acquire and release on the borrowed connection`() {
        val script = SessionJdbcScript(listOf(1, 1))
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val acquired = scope.acquireSessionAdvisoryLock(acquireApi())
            val released = scope.releaseSessionAdvisoryLock(releaseApi())

            assertEquals(AcquireSessionLockResult.Acquired, assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(acquired).result)
            assertEquals(ReleaseSessionLockResult.Released, assertIs<ExecuteResult.Success<ReleaseSessionLockResult>>(released).result)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        assertEquals(
            listOf(
                "SELECT GET_LOCK(?, ?) AS acquired",
                "SELECT RELEASE_LOCK(?) AS released",
            ),
            script.sql,
        )
        assertEquals(
            listOf(
                Binding(1, "SELECT GET_LOCK(?, ?) AS acquired", lockName(), Types.VARCHAR),
                Binding(2, "SELECT GET_LOCK(?, ?) AS acquired", 0, Types.INTEGER),
                Binding(1, "SELECT RELEASE_LOCK(?) AS released", lockName(), Types.VARCHAR),
            ),
            script.bindings,
        )
        assertEquals(2, script.statementCloseCount)
        assertEquals(2, script.resultSetCloseCount)
        assertEquals(1, script.connectionCloseCount)
        assertEquals(2, script.moreResultsCount)
        assertEquals(0, script.commitCount)
        assertEquals(0, script.rollbackCount)
    }

    @Test
    fun `legacy AST overload still executes acquire and release`() {
        val script = SessionJdbcScript(listOf(1, 1))
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val acquired = scope.acquireSessionAdvisoryLock(acquireAst())
            val released = scope.releaseSessionAdvisoryLock(releaseAst())

            assertEquals(
                AcquireSessionLockResult.Acquired,
                assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(acquired).result,
            )
            assertEquals(
                ReleaseSessionLockResult.Released,
                assertIs<ExecuteResult.Success<ReleaseSessionLockResult>>(released).result,
            )
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        assertEquals(2, script.prepareCount)
        assertEquals(2, script.statementCloseCount)
        assertEquals(2, script.resultSetCloseCount)
    }

    @Test
    fun `acquire timeout and release not owned are successful semantic outcomes`() {
        val script = SessionJdbcScript(listOf(0, 0))
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val acquire = scope.acquireSessionAdvisoryLock(acquireApi())
            val release = scope.releaseSessionAdvisoryLock(releaseApi())

            assertEquals(AcquireSessionLockResult.TimedOut, assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(acquire).result)
            assertEquals(ReleaseSessionLockResult.NotOwned, assertIs<ExecuteResult.Success<ReleaseSessionLockResult>>(release).result)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
    }

    @Test
    fun `mysql and mariadb platform configurations execute acquire with compatible sql`() {
        listOf(DialectQuery.MY_SQL, DialectQuery.MARIA_DB).forEach { platformDialect ->
            val script = SessionJdbcScript(listOf(1))
            val context = databaseContext(script.connection, platformDialect)

            val result = try {
                OperationExecutionExecutor(context).connection { scope ->
                    val acquire = scope.acquireSessionAdvisoryLock(acquireAst())
                    assertEquals(
                        AcquireSessionLockResult.Acquired,
                        assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(acquire).result,
                    )
                    ExecuteResult.Success(Unit)
                }
            } finally {
                context.close()
            }

            assertIs<ExecuteResult.Success<Unit>>(result)
            assertEquals(listOf("SELECT GET_LOCK(?, ?) AS acquired"), script.sql)
            assertEquals(1, script.connectionCloseCount)
        }
    }

    @Test
    fun `session execution failure is latched and blocks later jdbc work`() {
        val sqlFailure = SQLException("prepare failed")
        val script = SessionJdbcScript(statuses = emptyList(), prepareFailure = sqlFailure)
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val first = assertIs<ExecuteResult.Failure>(scope.acquireSessionAdvisoryLock(acquireApi()))
            val second = assertIs<ExecuteResult.Failure>(scope.releaseSessionAdvisoryLock(releaseApi()))

            assertSame(first.exception, second.exception)
            assertSame(sqlFailure, first.exception.cause)
            ExecuteResult.Success("the failure latch must win")
        }

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertSame(sqlFailure, failure.exception.cause)
        assertEquals(1, script.prepareCount)
        assertEquals(0, script.statementCloseCount)
        assertEquals(1, script.connectionCloseCount)
    }

    @Test
    fun `a prior non session operation failure blocks acquire without another jdbc call`() {
        val priorSqlFailure = SQLException("prior DDL prepare failed")
        val script = SessionJdbcScript(statuses = emptyList(), prepareFailure = priorSqlFailure)
        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val prior = assertIs<ExecuteResult.Failure>(scope.executeDdl("CREATE TABLE prior_failure (id INT)"))
            val acquire = assertIs<ExecuteResult.Failure>(scope.acquireSessionAdvisoryLock(acquireApi()))

            assertSame(prior.exception, acquire.exception)
            assertSame(priorSqlFailure, acquire.exception)
            val genericAcquire = assertIs<ExecuteResult.Failure>(scope.session(acquireApi()).execute())
            assertSame(prior.exception, genericAcquire.exception)
            ExecuteResult.Success(Unit)
        }

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertSame(priorSqlFailure, failure.exception)
        assertEquals(1, script.prepareCount)
        assertEquals(0, script.statementCloseCount)
        assertEquals(1, script.connectionCloseCount)
    }

    @Test
    fun `acquire jdbc error is recorded fatal rethrown unchanged and connection closes with scope`() {
        val fatal = AssertionError("acquire execute failed")
        val script = SessionJdbcScript(statuses = listOf(1), executeError = fatal)
        var scopeSeen: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { script.connection }.connection { scope ->
                scopeSeen = scope
                scope.acquireSessionAdvisoryLock(acquireApi())
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scopeSeen?.fatalError)
        assertEquals(1, script.prepareCount)
        assertEquals(1, script.statementCloseCount)
        assertEquals(0, script.connectionCloseCountWhenStatementClosed)
        assertEquals(1, script.connectionCloseCount)
        assertEquals(0, script.commitCount)
        assertEquals(0, script.rollbackCount)
    }

    @Test
    fun `release jdbc error is recorded fatal rethrown unchanged and connection closes with scope`() {
        val fatal = AssertionError("release execute failed")
        val script = SessionJdbcScript(statuses = listOf(1), executeError = fatal)
        var scopeSeen: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { script.connection }.connection { scope ->
                scopeSeen = scope
                scope.releaseSessionAdvisoryLock(releaseApi())
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scopeSeen?.fatalError)
        assertEquals(1, script.prepareCount)
        assertEquals(1, script.statementCloseCount)
        assertEquals(0, script.connectionCloseCountWhenStatementClosed)
        assertEquals(1, script.connectionCloseCount)
        assertEquals(0, script.commitCount)
        assertEquals(0, script.rollbackCount)
    }

    @Test
    fun `root transaction and transaction child reject session before preparing sql`() {
        val rootTransactionScript = SessionJdbcScript(emptyList())
        val rootTransactionResult = OperationTransactionExecutor(
            connectionFactory = { rootTransactionScript.connection },
            databasePlatformConfig = DatabasePlatformConfig(DialectQuery.MY_SQL),
        ).transaction { scope ->
            assertIs<ExecuteResult.Failure>(scope.acquireSessionAdvisoryLock(acquireApi()))
            assertIs<ExecuteResult.Failure>(scope.session(releaseApi()).execute())
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(rootTransactionResult)
        assertEquals(0, rootTransactionScript.prepareCount)
        assertEquals(1, rootTransactionScript.rollbackCount)
        assertEquals(1, rootTransactionScript.connectionCloseCount)

        val childScript = SessionJdbcScript(emptyList())
        val childResult = OperationExecutionExecutor { childScript.connection }.connection { root ->
            val transaction = root.transaction { child ->
                assertIs<ExecuteResult.Failure>(child.releaseSessionAdvisoryLock(releaseApi()))
                assertIs<ExecuteResult.Failure>(child.session(acquireApi()).execute())
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(transaction)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(childResult)
        assertEquals(0, childScript.prepareCount)
        assertEquals(1, childScript.rollbackCount)
        assertEquals(1, childScript.connectionCloseCount)
    }

    @Test
    fun `unmanaged scope rejects session commands before jdbc`() {
        val script = SessionJdbcScript(emptyList())
        val scope = OperationExecutionScope(
            script.connection,
            DatabasePlatformConfig(DialectQuery.MY_SQL),
        )

        val result = scope.acquireSessionAdvisoryLock(acquireApi())
        val genericResult = scope.session(releaseApi()).execute()

        assertIs<ExecuteResult.Failure>(result)
        assertIs<ExecuteResult.Failure>(genericResult)
        assertEquals(0, script.prepareCount)
        scope.end()
        assertEquals(0, script.connectionCloseCount)
    }

    @Test
    fun `pipeline stage session consumer is deferred and guarded at creation and execution`() {
        var delegateConsumerCreations = 0
        var delegateExecutions = 0
        val commandExecutor = object : ISessionCommandExecutor {
            override fun acquire(ast: IAcquireSessionAdvisoryLockAst): AcquireSessionLockResult =
                AcquireSessionLockResult.Acquired

            override fun release(ast: IReleaseSessionAdvisoryLockAst): ReleaseSessionLockResult =
                ReleaseSessionLockResult.Released
        }
        val delegate = object : OperationExecutionContext {
            override fun executeDdl(
                sql: String,
                parameters: List<OperationParameter>,
            ): ExecuteResult<Boolean> = ExecuteResult.Success(true)

            override fun <A : ISessionCommandAst, R : SessionExecutionResult> session(
                api: ISessionCommandApi<A, R>,
            ): SessionConsumer<R> {
                delegateConsumerCreations++
                return SessionConsumer {
                    delegateExecutions++
                    ExecuteResult.Success(api.dispatch(commandExecutor))
                }
            }

            override fun <R> failed(failure: Throwable): ExecuteResult<R> = ExecuteResult.Failure(failure)
        }

        val activeStage = OperationPipelineStageContext<Unit>(delegate, Thread.currentThread(), hasNextStage = false)
        val consumer = activeStage.session(acquireApi())
        assertEquals(1, delegateConsumerCreations)
        assertEquals(0, delegateExecutions)

        val result: ExecuteResult<AcquireSessionLockResult> = consumer.execute()
        assertSame(
            AcquireSessionLockResult.Acquired,
            assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(result).result,
        )
        assertEquals(1, delegateExecutions)

        activeStage.expire()
        assertFailsWith<IllegalStateException> { consumer.execute() }
        assertEquals(1, delegateConsumerCreations)
        assertEquals(1, delegateExecutions)

        val expiredStage = OperationPipelineStageContext<Unit>(delegate, Thread.currentThread(), hasNextStage = false)
        expiredStage.expire()
        assertFailsWith<IllegalStateException> { expiredStage.session(acquireApi()) }
        assertEquals(1, delegateConsumerCreations)

        val crossThreadStage = OperationPipelineStageContext<Unit>(delegate, Thread.currentThread(), hasNextStage = false)
        val crossThreadConsumer = crossThreadStage.session(acquireApi())
        var threadFailure: Throwable? = null
        val worker = thread(start = true) {
            threadFailure = runCatching { crossThreadConsumer.execute() }.exceptionOrNull()
        }
        worker.join()

        assertIs<IllegalStateException>(threadFailure)
        assertEquals(2, delegateConsumerCreations)
        assertEquals(1, delegateExecutions)
        crossThreadStage.expire()
    }

    @Test
    fun `pipeline stage wrapper preserves the scope consumer one shot state`() {
        val script = SessionJdbcScript(listOf(1, 1))
        val operationResult = OperationExecutionExecutor { script.connection }.connection { scope ->
            val stage = OperationPipelineStageContext<Unit>(scope, Thread.currentThread(), hasNextStage = false)
            val consumer = stage.session(acquireApi())
            assertEquals(0, script.prepareCount)

            val first: ExecuteResult<AcquireSessionLockResult> = consumer.execute()
            assertSame(
                AcquireSessionLockResult.Acquired,
                assertIs<ExecuteResult.Success<AcquireSessionLockResult>>(first).result,
            )
            val second = assertIs<ExecuteResult.Failure>(consumer.execute())
            assertIs<IllegalStateException>(second.exception)
            assertEquals(1, script.prepareCount)

            stage.expire()
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(operationResult)
        assertEquals(1, script.prepareCount)
        assertEquals(1, script.connectionCloseCount)
    }

    @Test
    fun `pipeline stage forwards results without relatching and enforces stage lifetime`() {
        val acquireFailure = ExecuteResult.Failure(IllegalStateException("delegate failure"))
        var acquireCalls = 0
        var releaseCalls = 0
        var typedAcquireCalls = 0
        var typedReleaseCalls = 0
        var failedCalls = 0
        val delegate = object : OperationExecutionContext {
            override fun executeDdl(
                sql: String,
                parameters: List<OperationParameter>,
            ): ExecuteResult<Boolean> = ExecuteResult.Success(true)

            override fun acquireSessionAdvisoryLock(
                api: IAcquireSessionAdvisoryLockApi,
            ): ExecuteResult<AcquireSessionLockResult> {
                typedAcquireCalls++
                @Suppress("UNCHECKED_CAST")
                return acquireFailure as ExecuteResult<AcquireSessionLockResult>
            }

            override fun acquireSessionAdvisoryLock(
                ast: IAcquireSessionAdvisoryLockAst,
            ): ExecuteResult<AcquireSessionLockResult> {
                acquireCalls++
                @Suppress("UNCHECKED_CAST")
                return acquireFailure as ExecuteResult<AcquireSessionLockResult>
            }

            override fun releaseSessionAdvisoryLock(
                api: IReleaseSessionAdvisoryLockApi,
            ): ExecuteResult<ReleaseSessionLockResult> {
                typedReleaseCalls++
                return ExecuteResult.Success(ReleaseSessionLockResult.NotOwned)
            }

            override fun releaseSessionAdvisoryLock(
                ast: IReleaseSessionAdvisoryLockAst,
            ): ExecuteResult<ReleaseSessionLockResult> {
                releaseCalls++
                return ExecuteResult.Success(ReleaseSessionLockResult.NotOwned)
            }

            override fun <T> failed(failure: Throwable): ExecuteResult<T> {
                failedCalls++
                return ExecuteResult.Failure(failure)
            }
        }

        val stage = OperationPipelineStageContext<Unit>(delegate, Thread.currentThread(), hasNextStage = true)
        assertSame(acquireFailure, stage.acquireSessionAdvisoryLock(acquireAst()))
        assertEquals(
            ReleaseSessionLockResult.NotOwned,
            assertIs<ExecuteResult.Success<ReleaseSessionLockResult>>(stage.releaseSessionAdvisoryLock(releaseAst())).result,
        )
        assertEquals(1, acquireCalls)
        assertEquals(1, releaseCalls)
        assertEquals(0, failedCalls)

        assertSame(acquireFailure, stage.acquireSessionAdvisoryLock(acquireApi()))
        assertEquals(
            ReleaseSessionLockResult.NotOwned,
            assertIs<ExecuteResult.Success<ReleaseSessionLockResult>>(stage.releaseSessionAdvisoryLock(releaseApi())).result,
        )
        assertEquals(1, typedAcquireCalls)
        assertEquals(1, typedReleaseCalls)

        stage.expire()
        assertFailsWith<IllegalStateException> { stage.acquireSessionAdvisoryLock(acquireApi()) }
        assertEquals(1, acquireCalls)
        assertEquals(1, typedAcquireCalls)

        val crossThreadStage = OperationPipelineStageContext<Unit>(delegate, Thread.currentThread(), hasNextStage = true)
        var threadFailure: Throwable? = null
        val worker = thread(start = true) {
            threadFailure = runCatching { crossThreadStage.releaseSessionAdvisoryLock(releaseApi()) }.exceptionOrNull()
        }
        worker.join()
        assertIs<IllegalStateException>(threadFailure)
        assertEquals(1, releaseCalls)
        assertEquals(1, typedReleaseCalls)
    }

    private fun verifyAcquireFluentApi(
        ctx: OperationExecutionContext,
        onSuccess: (AcquireSessionLockResult) -> Unit,
        onError: (Throwable) -> Unit,
    ): ExecuteResult<Unit> {
        val result: ExecuteResult<AcquireSessionLockResult> = ctx.session(
            acquireSessionAdvisoryLock {
                resource("application", "cache-refresh")
                noWait()
            },
        ).execute()

        return result.success { value: AcquireSessionLockResult ->
            onSuccess(value)
            when (value) {
                AcquireSessionLockResult.Acquired -> Unit
                AcquireSessionLockResult.TimedOut -> Unit
            }
        }.error { failure: Throwable ->
            onError(failure)
            failure.message
        }
    }

    private fun verifyReleaseFluentApi(
        ctx: OperationExecutionContext,
        onSuccess: (ReleaseSessionLockResult) -> Unit,
        onError: (Throwable) -> Unit,
    ): ExecuteResult<Unit> {
        val result: ExecuteResult<ReleaseSessionLockResult> = ctx.session(
            releaseSessionAdvisoryLock {
                resource("application", "cache-refresh")
            },
        ).execute()

        return result.success { value: ReleaseSessionLockResult ->
            onSuccess(value)
            when (value) {
                ReleaseSessionLockResult.Released -> Unit
                ReleaseSessionLockResult.NotOwned -> Unit
            }
        }.error { failure: Throwable ->
            onError(failure)
            failure.message
        }
    }

    private fun acquireApi(): IAcquireSessionAdvisoryLockApi = acquireSessionAdvisoryLock {
        resource("application", "integration")
        noWait()
    }

    private fun releaseApi(): IReleaseSessionAdvisoryLockApi = releaseSessionAdvisoryLock {
        resource("application", "integration")
    }

    private fun acquireAst(): IAcquireSessionAdvisoryLockAst = AcquireSessionAdvisoryLockAst(
        AcquireSessionAdvisoryLockAstMetadata().apply {
            resource = LockResource("application", "integration")
            waitPolicy = LockWaitPolicy.NoWait
        },
    )

    private fun releaseAst(): IReleaseSessionAdvisoryLockAst = ReleaseSessionAdvisoryLockAst(
        ReleaseSessionAdvisoryLockAstMetadata().apply {
            resource = LockResource("application", "integration")
        },
    )

    private fun lockName(): String = SessionAdvisoryLockNameGenerator.generate(
        LockResource("application", "integration"),
    )

    private fun databaseContext(connection: Connection, dialect: DialectQuery): DatabaseContext = DatabaseContext(
        name = "session-integration-test",
        config = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://localhost", 3306, "test", "user", ""),
            platform = DatabasePlatformConfig(dialect),
        ),
        connectionProvider = object : IDatabaseConnection {
            override fun build(): Connection = connection
        },
    )

    private data class Binding(
        val index: Int,
        val sql: String,
        val value: Any?,
        val sqlType: Int,
    )

    private class SessionJdbcScript(
        statuses: List<Int?>,
        private val prepareFailure: SQLException? = null,
        private val executeError: Error? = null,
    ) {
        private val scriptedStatuses = statuses.toMutableList()
        val sql = mutableListOf<String>()
        val bindings = mutableListOf<Binding>()
        var prepareCount = 0
            private set
        var statementCloseCount = 0
            private set
        var resultSetCloseCount = 0
            private set
        var moreResultsCount = 0
            private set
        var connectionCloseCount = 0
            private set
        var connectionCloseCountWhenStatementClosed: Int? = null
            private set
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set
        var autoCommitChangeCount = 0
            private set

        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    prepareCount++
                    prepareFailure?.let { throw it }
                    val statementSql = args!![0] as String
                    sql += statementSql
                    statement(statementSql)
                }
                "close" -> { connectionCloseCount++; null }
                "setAutoCommit" -> { autoCommitChangeCount++; null }
                "getAutoCommit" -> true
                "commit" -> { commitCount++; null }
                "rollback" -> { rollbackCount++; null }
                "isClosed" -> false
                else -> jdbcDefaultValue(method.returnType)
            }
        } as Connection

        private fun statement(statementSql: String): PreparedStatement {
            val status = scriptedStatuses.removeAt(0)
            val resultSet = resultSet(status)
            return Proxy.newProxyInstance(
                PreparedStatement::class.java.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "setNull" -> {
                        bindings += Binding(args!![0] as Int, statementSql, null, args[1] as Int)
                        null
                    }
                    "setObject" -> {
                        bindings += Binding(args!![0] as Int, statementSql, args[1], args[2] as Int)
                        null
                    }
                    "execute" -> {
                        executeError?.let { throw it }
                        true
                    }
                    "getResultSet" -> resultSet
                    "getUpdateCount" -> -1
                    "getMoreResults" -> {
                        moreResultsCount++
                        resultSet.close()
                        false
                    }
                    "close" -> {
                        statementCloseCount++
                        connectionCloseCountWhenStatementClosed = connectionCloseCount
                        null
                    }
                    "isClosed" -> false
                    "toString" -> "SessionJdbcStatement($statementSql)"
                    else -> jdbcDefaultValue(method.returnType)
                }
            } as PreparedStatement
        }

        private fun resultSet(status: Int?): ResultSet {
            var consumed = false
            var closed = false
            val metadata = Proxy.newProxyInstance(
                ResultSetMetaData::class.java.classLoader,
                arrayOf(ResultSetMetaData::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getColumnCount" -> 1
                    else -> jdbcDefaultValue(method.returnType)
                }
            } as ResultSetMetaData

            return Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getMetaData" -> metadata
                    "next" -> if (consumed) false else { consumed = true; true }
                    "getObject" -> {
                        check(args!![0] == 1)
                        status
                    }
                    "close" -> if (!closed) { closed = true; resultSetCloseCount++; null } else null
                    "isClosed" -> closed
                    else -> jdbcDefaultValue(method.returnType)
                }
            } as ResultSet
        }
    }

}

private fun jdbcDefaultValue(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE -> 0.toByte()
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0.0f
    java.lang.Double.TYPE -> 0.0
    java.lang.Character.TYPE -> '\u0000'
    else -> null
}
