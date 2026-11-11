package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.SystemLock
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SessionAdvisoryLockTest {
    @Test
    fun `successful lock runs callback once and releases before Operation closes the same Connection`() {
        val script = LockScript()
        var callbackCount = 0
        var callbackScope: OperationExecutionScope? = null

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 12) { suppliedScope ->
                callbackCount++
                callbackScope = suppliedScope
                script.events += "callback"
                assertSame(scope, suppliedScope)
                ExecuteResult.Success("done")
            }
        }

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(1, callbackCount)
        assertTrue(script.connectionUsedForStatements)
        assertEquals(
            listOf(
                "prepare:acquire", "execute:acquire", "resultSet.close:acquire", "statement.close:acquire",
                "callback",
                "prepare:release", "execute:release", "resultSet.close:release", "statement.close:release",
                "close",
            ),
            script.events,
        )
        assertTrue(script.events.indexOf("execute:release") < script.events.indexOf("close"))
        assertFalse(script.events.any { it.startsWith("autoCommit:") || it == "commit" || it == "rollback" })
        assertEquals(
            listOf(
                Binding(1, "app:migration:test", Types.VARCHAR),
                Binding(2, 12, Types.INTEGER),
                Binding(1, "app:migration:test", Types.VARCHAR),
            ),
            script.bindings,
        )
        assertSame(script.connection, script.connectionUsedForStatementsObject)
        assertTrue(callbackScope != null)
    }

    @Test
    fun `timeout does not call callback or release`() {
        val script = LockScript(acquireValue = 0)
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 0) {
                callbackCount++
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<TimeoutException>(assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, callbackCount)
        assertFalse(script.events.contains("prepare:release"))
        assertEquals(1, script.events.count { it == "execute:acquire" })
        assertEquals("close", script.events.last())
    }

    @Test
    fun `SQL NULL from acquire is a failure and does not call callback or release`() {
        val script = LockScript(acquireValue = null)
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 1) {
                callbackCount++
                ExecuteResult.Success(Unit)
            }
        }

        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("SQL NULL"))
        assertEquals(0, callbackCount)
        assertFalse(script.events.contains("prepare:release"))
    }

    @Test
    fun `Acquire SQL exception is returned unchanged and does not call callback or release`() {
        val acquireFailure = SQLException("acquire failed")
        val script = LockScript(acquireFailure = acquireFailure)
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 1) {
                callbackCount++
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(acquireFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, callbackCount)
        assertFalse(script.events.contains("prepare:release"))
    }

    @Test
    fun `confirmed Acquire is released if statement cleanup fails and callback is not started`() {
        val acquireCleanup = SQLException("acquire statement close failed")
        val script = LockScript().apply { acquireStatementCloseFailure = acquireCleanup }
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 1) {
                callbackCount++
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(acquireCleanup, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, callbackCount)
        assertEquals(1, script.events.count { it == "execute:release" })
        assertTrue(script.events.indexOf("execute:release") < script.events.indexOf("close"))
    }

    @Test
    fun `Release zero and NULL are failures`() {
        for (releaseValue in listOf<Int?>(0, null)) {
            val script = LockScript(releaseValue = releaseValue)
            var callbackCount = 0

            val result = OperationExecutionExecutor { script.connection }.connection { scope ->
                scope.withSessionAdvisoryLock("app:migration:test", 1) {
                    callbackCount++
                    ExecuteResult.Success(Unit)
                }
            }

            assertIs<ExecuteResult.Failure>(result)
            assertEquals(1, callbackCount)
            assertEquals(1, script.events.count { it == "execute:release" })
        }
    }

    @Test
    fun `callback Failure is preserved and Release still runs`() {
        val script = LockScript()
        val primary = SQLException("migration failed")
        var callbackCount = 0
        var scopeSeen: OperationExecutionScope? = null

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scopeSeen = scope
            scope.withSessionAdvisoryLock("app:migration:test", 1) {
                callbackCount++
                ExecuteResult.Failure(primary)
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(primary, scopeSeen?.firstFailure)
        assertEquals(1, callbackCount)
        assertTrue(script.events.contains("execute:release"))
    }

    @Test
    fun `callback exception becomes Failure and Release still runs`() {
        val script = LockScript()
        val primary = IllegalStateException("callback threw")
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock<Unit>("app:migration:test", 1) {
                callbackCount++
                throw primary
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, callbackCount)
        assertTrue(script.events.contains("execute:release"))
    }

    @Test
    fun `callback Error remains fatal and Release is attempted before connection close`() {
        val script = LockScript()
        val fatal = AssertionError("fatal callback")
        var scopeSeen: OperationExecutionScope? = null
        var callbackCount = 0

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { script.connection }.connection { scope ->
                scopeSeen = scope
                scope.withSessionAdvisoryLock<Unit>("app:migration:test", 1) {
                    callbackCount++
                    throw fatal
                }
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scopeSeen?.fatalError)
        assertEquals(1, callbackCount)
        assertTrue(script.events.indexOf("execute:release") < script.events.indexOf("close"))
    }

    @Test
    fun `callback failure remains primary and Release SQL failure is suppressed`() {
        val cleanup = SQLException("release SQL failed")
        val script = LockScript(releaseFailure = cleanup)
        val primary = IllegalStateException("callback failed")
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock<Unit>("app:migration:test", 1) {
                callbackCount++
                throw primary
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf(cleanup), primary.suppressed.toList())
        assertEquals(1, callbackCount)
        assertTrue(script.events.contains("prepare:release"))
    }

    @Test
    fun `callback failure remains primary when successful Release statement cleanup fails`() {
        val cleanup = SQLException("release statement close failed")
        val script = LockScript().apply { releaseStatementCloseFailure = cleanup }
        val primary = IllegalStateException("callback failed")

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock<Unit>("app:migration:test", 1) {
                throw primary
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf(cleanup), primary.suppressed.toList())
        assertTrue(script.events.contains("execute:release"))
        assertTrue(script.events.indexOf("statement.close:release") < script.events.indexOf("close"))
    }

    @Test
    fun `callback Error remains primary and Release failure is suppressed`() {
        val cleanup = SQLException("release failed")
        val script = LockScript(releaseFailure = cleanup)
        val fatal = AssertionError("fatal callback")

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { script.connection }.connection { scope ->
                scope.withSessionAdvisoryLock<Unit>("app:migration:test", 1) {
                    throw fatal
                }
            }
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(cleanup), fatal.suppressed.toList())
        assertTrue(script.events.indexOf("prepare:release") < script.events.indexOf("close"))
    }

    @Test
    fun `successful callback with Release failure returns Failure`() {
        val cleanup = SQLException("release failed")
        val script = LockScript(releaseFailure = cleanup)
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 1) {
                callbackCount++
                ExecuteResult.Success("work completed")
            }
        }

        assertSame(cleanup, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, callbackCount)
    }

    @Test
    fun `latched scope failure does not prevent private Release cleanup`() {
        val script = LockScript()
        val primary = SQLException("scope already failed")
        var callbackCount = 0
        var scopeSeen: OperationExecutionScope? = null

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scopeSeen = scope
            scope.withSessionAdvisoryLock("app:migration:test", 1) { activeScope ->
                callbackCount++
                activeScope.failed<Unit>(primary)
                ExecuteResult.Success("must not erase scope failure")
            }
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(primary, scopeSeen?.firstFailure)
        assertEquals(1, callbackCount)
        assertTrue(script.events.contains("execute:release"))
    }

    @Test
    fun `broken Connection during callback reports Release failure and Operation still closes it`() {
        val script = LockScript()
        val brokenConnectionFailure = SQLException("connection broke before release")
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 1) {
                callbackCount++
                script.failReleasePrepareWith = brokenConnectionFailure
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(brokenConnectionFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, callbackCount)
        assertTrue(script.events.contains("prepare:release"))
        assertEquals("close", script.events.last())
    }

    @Test
    fun `transaction child scope is rejected before acquiring a lock`() {
        val script = LockScript()
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { parent ->
            parent.transaction { child ->
                child.withSessionAdvisoryLock("app:migration:test", 1) {
                    callbackCount++
                    ExecuteResult.Success(Unit)
                }
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, callbackCount)
        assertFalse(script.events.any { it.startsWith("prepare:") })
        assertFalse(script.events.any { it.startsWith("execute:") })
    }

    @Test
    fun `scope created outside Operation lifecycle is rejected`() {
        val script = LockScript()
        val scope = OperationExecutionScope(script.connection, DatabasePlatformConfig(DialectQuery.MY_SQL))

        val result = scope.withSessionAdvisoryLock("app:migration:test", 1) {
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("managed"))
        assertFalse(script.events.any { it.startsWith("prepare:") })
    }

    @Test
    fun `non-owner thread is rejected before acquiring a lock`() {
        val script = LockScript()
        var workerResult: ExecuteResult<Unit>? = null
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            val worker = thread(start = true) {
                workerResult = scope.withSessionAdvisoryLock("app:migration:test", 1) {
                    callbackCount++
                    ExecuteResult.Success(Unit)
                }
            }
            worker.join()
            ExecuteResult.Success("ignored due to foreign-thread misuse")
        }

        assertIs<ExecuteResult.Failure>(workerResult)
        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, callbackCount)
        assertFalse(script.events.any { it.startsWith("prepare:") })
    }

    @Test
    fun `nested lock is rejected and outer lock is released once`() {
        val script = LockScript()
        var callbackCount = 0

        val result = OperationExecutionExecutor { script.connection }.connection { scope ->
            scope.withSessionAdvisoryLock("app:migration:test", 1) { activeScope ->
                callbackCount++
                activeScope.withSessionAdvisoryLock("app:migration:nested", 1) {
                    callbackCount++
                    ExecuteResult.Success(Unit)
                }
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, callbackCount)
        assertEquals(1, script.events.count { it == "execute:acquire" })
        assertEquals(1, script.events.count { it == "execute:release" })
    }

    @Test
    fun `invalid names and timeout bounds fail closed without SQL`() {
        val invalidNames = listOf("", "   ", "non-ascii-մ", "x".repeat(65))
        for (name in invalidNames) {
            val script = LockScript()
            val result = OperationExecutionExecutor { script.connection }.connection { scope ->
                scope.withSessionAdvisoryLock(name, 1) { ExecuteResult.Success(Unit) }
            }
            assertIs<ExecuteResult.Failure>(result)
            assertFalse(script.events.any { it.startsWith("prepare:") })
        }

        for (timeout in listOf(-1, 301)) {
            val script = LockScript()
            val result = OperationExecutionExecutor { script.connection }.connection { scope ->
                scope.withSessionAdvisoryLock("app:migration:test", timeout) { ExecuteResult.Success(Unit) }
            }
            assertIs<ExecuteResult.Failure>(result)
            assertFalse(script.events.any { it.startsWith("prepare:") })
        }
    }

    @Test
    fun `system lock name is deterministic bounded and combines lock and database identity`() {
        val identity = configuredIdentity(host = "db-a.internal", database = "catalog_a")
        val firstScript = LockScript()
        var callbackCount = 0

        val firstResult = runSystemLock(firstScript, identity, SystemLock.MIGRATION) { root, locked ->
            callbackCount++
            assertSame(root, locked)
            assertEquals(identity, locked.databaseIdentity)
            assertSame(firstScript.connection, locked.connection)
            firstScript.events += "callback"
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(firstResult)
        assertEquals(1, callbackCount)
        assertEquals(1, firstScript.connectionBuildCount)
        val firstNames = lockNames(firstScript)
        assertEquals(2, firstNames.size)
        assertEquals(firstNames[0], firstNames[1])
        assertTrue(firstNames.first().length <= 64)
        assertTrue(firstNames.first().all { it.code in 0x20..0x7e })
        assertTrue(firstScript.events.indexOf("execute:acquire") < firstScript.events.indexOf("callback"))
        assertTrue(firstScript.events.indexOf("callback") < firstScript.events.indexOf("execute:release"))
        assertFalse(firstScript.events.any { it == "commit" || it == "rollback" || it.startsWith("autoCommit:") })
        assertTrue(firstScript.connectionUsedForStatements)

        val repeatedScript = LockScript()
        assertIs<ExecuteResult.Success<Unit>>(
            runSystemLock(repeatedScript, identity, SystemLock.MIGRATION) { _, _ -> ExecuteResult.Success(Unit) },
        )
        assertEquals(firstNames.first(), lockNames(repeatedScript).first())

        val otherLockScript = LockScript()
        runSystemLock(otherLockScript, identity, SystemLock.SCHEMA_UPDATE) { _, _ -> ExecuteResult.Success(Unit) }
        assertNotEquals(firstNames.first(), lockNames(otherLockScript).first())

        val otherDatabaseScript = LockScript()
        runSystemLock(
            otherDatabaseScript,
            configuredIdentity(host = "db-b.internal", database = "catalog_a"),
            SystemLock.MIGRATION,
        ) { _, _ -> ExecuteResult.Success(Unit) }
        assertNotEquals(firstNames.first(), lockNames(otherDatabaseScript).first())
    }

    @Test
    fun `system lock uses both supported dialects through the same managed scope`() {
        for (dialect in listOf(DialectQuery.MY_SQL, DialectQuery.MARIA_DB)) {
            val script = LockScript()
            val result = runSystemLock(
                script,
                configuredIdentity(host = "db.internal", database = "catalog"),
                SystemLock.MIGRATION,
                dialect,
            ) { root, locked ->
                assertSame(root, locked)
                ExecuteResult.Success(Unit)
            }

            assertIs<ExecuteResult.Success<Unit>>(result)
            assertEquals(1, script.connectionBuildCount)
            assertSame(script.connection, script.connectionUsedForStatementsObject)
            assertTrue(script.events.contains("execute:acquire"))
            assertTrue(script.events.contains("execute:release"))
        }
    }

    @Test
    fun `missing database identity and invalid system lock fail closed`() {
        var callbackCount = 0
        val noIdentityScript = LockScript()
        val missingIdentity = runSystemLock(noIdentityScript, null, SystemLock.MIGRATION) { _, _ ->
            callbackCount++
            ExecuteResult.Success(Unit)
        }
        assertIs<ExecuteResult.Failure>(missingIdentity)
        assertTrue(assertIs<ExecuteResult.Failure>(missingIdentity).exception.message.orEmpty().contains("DatabaseIdentity"))
        assertEquals(0, callbackCount)
        assertFalse(noIdentityScript.events.any { it.startsWith("prepare:") })

        val invalidLockScript = LockScript()
        val invalidLock = runSystemLock(
            invalidLockScript,
            configuredIdentity(host = "db.internal", database = "catalog"),
            SystemLock("invalid lock name"),
        ) { _, _ ->
            callbackCount++
            ExecuteResult.Success(Unit)
        }
        assertIs<ExecuteResult.Failure>(invalidLock)
        assertEquals(0, callbackCount)
        assertFalse(invalidLockScript.events.any { it.startsWith("prepare:") })
    }

    @Test
    fun `system lock rejects unmanaged scope without running callback`() {
        val script = LockScript()
        val identity = configuredIdentity(host = "db.internal", database = "catalog")
        val scope = OperationExecutionScope(
            script.connection,
            DatabasePlatformConfig(DialectQuery.MY_SQL),
            identity,
        )
        var callbackCount = 0

        val result = scope.withSystemLock(SystemLock.MIGRATION, 1) {
            callbackCount++
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("managed"))
        assertEquals(0, callbackCount)
        assertFalse(script.events.any { it.startsWith("prepare:") })
    }

    @Test
    fun `system lock rejects a child transaction scope without running callback`() {
        val script = LockScript()
        val identity = configuredIdentity(host = "db.internal", database = "catalog")
        val context = createSystemLockContext(
            script,
            identity,
            DialectQuery.MY_SQL,
        )
        var callbackCount = 0

        val result = try {
            OperationExecutionExecutor(context).connection { root ->
                root.transaction { child ->
                    assertEquals(identity, child.databaseIdentity)
                    child.withSystemLock(SystemLock.MIGRATION, 1) {
                        callbackCount++
                        ExecuteResult.Success(Unit)
                    }
                }
            }
        } finally {
            context.close()
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, callbackCount)
        assertFalse(script.events.any { it.startsWith("prepare:") })
        assertEquals(1, script.connectionBuildCount)
    }

    @Test
    fun `system lock preserves callback failure when release fails`() {
        val cleanup = SQLException("system lock release failed")
        val primary = IllegalStateException("system work failed")
        val script = LockScript(releaseFailure = cleanup)
        var callbackCount = 0

        val result = runSystemLock(
            script,
            configuredIdentity(host = "db.internal", database = "catalog"),
            SystemLock.DATABASE_INITIALIZATION,
        ) { _, _ ->
            callbackCount++
            ExecuteResult.Failure(primary)
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf(cleanup), primary.suppressed.toList())
        assertEquals(1, callbackCount)
        assertTrue(script.events.indexOf("execute:release") < script.events.indexOf("close"))
    }

    private fun <T> runSystemLock(
        script: LockScript,
        identity: DatabaseIdentity?,
        systemLock: SystemLock,
        dialect: DialectQuery = DialectQuery.MY_SQL,
        block: (OperationExecutionScope, OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val context = createSystemLockContext(script, identity, dialect)
        return try {
            OperationExecutionExecutor(context).connection { rootScope ->
                rootScope.withSystemLock(systemLock, 1) { lockScope -> block(rootScope, lockScope) }
            }
        } finally {
            context.close()
        }
    }

    private fun createSystemLockContext(
        script: LockScript,
        identity: DatabaseIdentity?,
        dialect: DialectQuery,
    ): DatabaseContext {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://db.internal",
                dbPort = 3306,
                dbName = "catalog",
                dbUserName = "user",
                dbPassword = "secret",
            ),
            platform = DatabasePlatformConfig(dialect),
        )
        val context = DatabaseContext(
            "system-lock-test",
            config,
            object : IDatabaseConnection {
                override fun build(): Connection {
                    script.connectionBuildCount++
                    return script.connection
                }
            },
            identity,
        )
        return context
    }

    private fun configuredIdentity(host: String, database: String): DatabaseIdentity {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://$host", 3306, database, "user", "secret"),
        )
        return requireNotNull(DatabaseIdentity.from(config))
    }

    private fun lockNames(script: LockScript): List<String> =
        script.bindings.filter { it.index == 1 }.map { it.value as String }

    private data class Binding(val index: Int, val value: Any?, val sqlType: Int)

    private class LockScript(
        private val acquireValue: Int? = 1,
        private val releaseValue: Int? = 1,
        val acquireFailure: SQLException? = null,
        private val releaseFailure: SQLException? = null,
    ) {
        val events = mutableListOf<String>()
        val bindings = mutableListOf<Binding>()
        var connectionUsedForStatementsObject: Connection? = null
        val connectionUsedForStatements: Boolean get() = connectionUsedForStatementsObject != null
        var failReleasePrepareWith: SQLException? = null
        var acquireStatementCloseFailure: SQLException? = null
        var releaseStatementCloseFailure: SQLException? = null
        var connectionBuildCount: Int = 0

        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args!![0] as String
                    val kind = if (sql.contains("GET_LOCK")) "acquire" else "release"
                    events += "prepare:$kind"
                    connectionUsedForStatementsObject = proxy as Connection
                    if (kind == "release") {
                        failReleasePrepareWith?.let { throw it }
                    }
                    preparedStatement(kind)
                }
                "close" -> { events += "close"; null }
                "setAutoCommit" -> { events += "autoCommit:${args!![0]}"; null }
                "commit", "rollback" -> { events += method.name; null }
                "getAutoCommit" -> true
                "isClosed" -> false
                "toString" -> "LockScriptConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun preparedStatement(kind: String): PreparedStatement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "setObject" -> {
                    bindings += Binding(args!![0] as Int, args[1], args[2] as Int)
                    null
                }
                "executeQuery" -> {
                    events += "execute:$kind"
                    val failure = if (kind == "acquire") acquireFailure else releaseFailure
                    if (failure != null) throw failure
                    resultSet(kind)
                }
                "close" -> {
                    events += "statement.close:$kind"
                    val closeFailure = if (kind == "acquire") {
                        acquireStatementCloseFailure
                    } else {
                        releaseStatementCloseFailure
                    }
                    closeFailure?.let { throw it }
                    null
                }
                "toString" -> "LockScriptStatement($kind)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        private fun resultSet(kind: String): ResultSet {
            var beforeFirst = true
            var wasNull = false
            val value = if (kind == "acquire") acquireValue else releaseValue
            return Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "next" -> if (beforeFirst) { beforeFirst = false; true } else false
                    "getInt" -> {
                        val label = args!![0] as String
                        require(label == if (kind == "acquire") "acquired" else "released")
                        wasNull = value == null
                        value ?: 0
                    }
                    "wasNull" -> wasNull
                    "close" -> { events += "resultSet.close:$kind"; null }
                    "toString" -> "LockScriptResultSet($kind)"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }
    }

    private companion object {
        fun defaultValue(type: Class<*>): Any? = when (type) {
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
