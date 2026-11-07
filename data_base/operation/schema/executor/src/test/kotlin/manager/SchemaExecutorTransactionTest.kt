package gog.my_project.data_base_schema.executor.manager

import gog.my_project.data_base_operation.config.data_base.DefaultDatabaseConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.MYSQL
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SchemaExecutorTransactionTest {
    @Test
    fun `multiple schema operations share the operation transaction connection`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val executedConnections = mutableListOf<Connection>()
        val executedQueries = mutableListOf<BuiltQuery>()
        val connection = connection(events, executedConnections)
        val rawQueryExecutor = queryExecutor { suppliedConnection, query ->
            executedConnections += suppliedConnection
            executedQueries += query
            ExecuteResult.Success(true)
        }
        val schemaExecutor = SchemaExecutor(
            queryExecutor = rawQueryExecutor,
            transactionExecutor = OperationTransactionExecutor { connection },
        )
        DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)

        try {
            val first = SchemaCreateIndexBuilder()
                .tableName("users")
                .name("idx_users_email")
                .column("email")
            val second = SchemaCreateIndexBuilder()
                .tableName("users")
                .name("idx_users_age")
                .column("age")

            val result = schemaExecutor.transaction { tx ->
                var firstResult: ExecuteResult<Boolean>? = null
                var secondResult: ExecuteResult<Boolean>? = null
                tx.execute(first, blockExecute = { firstResult = it })
                tx.execute(second, blockExecute = { secondResult = it })
                if (firstResult is ExecuteResult.Failure) firstResult!!
                else if (secondResult is ExecuteResult.Failure) secondResult!!
                else ExecuteResult.Success("done")
            }

            assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
            assertEquals(2, executedConnections.size)
            assertSame(connection, executedConnections[0])
            assertSame(connection, executedConnections[1])
            assertEquals(
                listOf(
                    "CREATE INDEX `idx_users_email` ON `users` (`email`)",
                    "CREATE INDEX `idx_users_age` ON `users` (`age`)",
                ),
                executedQueries.map { it.query },
            )
            assertEquals(listOf("autoCommit:false", "commit", "close"), events)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    @Test
    fun `schema failure is latched and rolls back even if callback returns success`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val failure = SQLException("ddl failed")
        val connection = connection(events, ddlFailure = failure)
        var executorCalls = 0
        val rawQueryExecutor = queryExecutor { _, _ ->
            executorCalls++
            ExecuteResult.Failure(failure)
        }
        val schemaExecutor = SchemaExecutor(
            queryExecutor = rawQueryExecutor,
            transactionExecutor = OperationTransactionExecutor { connection },
        )
        DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)

        try {
            val operation = SchemaCreateIndexBuilder()
                .tableName("users")
                .name("idx_users_email")
                .column("email")

            val result = schemaExecutor.transaction { tx ->
                tx.execute(operation, blockExecute = { /* deliberately ignored */ })
                ExecuteResult.Success("ignored")
            }

            assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(1, executorCalls)
            assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    @Test
    fun `root schema execution is rejected while its transaction scope is active`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val connection = connection(events)
        var independentExecutions = 0
        val rawQueryExecutor = queryExecutor { _, _ ->
            independentExecutions++
            ExecuteResult.Success(true)
        }
        val schemaExecutor = SchemaExecutor(
            queryExecutor = rawQueryExecutor,
            transactionExecutor = OperationTransactionExecutor { connection },
        )
        DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)

        try {
            val operation = SchemaCreateIndexBuilder()
                .tableName("users")
                .name("idx_users_email")
                .column("email")
            var rootResult: ExecuteResult<Boolean>? = null

            val result = schemaExecutor.transaction { _ ->
                val worker = thread(start = true) {
                    schemaExecutor.execute(operation, blockExecute = { rootResult = it })
                }
                worker.join()
                ExecuteResult.Success("ignored root execution")
            }

            assertIs<ExecuteResult.Failure>(rootResult)
            assertIs<ExecuteResult.Failure>(result)
            assertEquals(0, independentExecutions)
            assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    @Test
    fun `rendered query and bindings use the injected executor on the operation-owned connection`() {
        val events = mutableListOf<String>()
        val preparedConnections = mutableListOf<Connection>()
        val connection = connection(events, preparedConnections)
        val context = databaseContext(connection)
        val query = BuiltQuery(
            "CREATE TABLE `sample` (`value` INT DEFAULT :value)",
            mutableListOf(QueryParameterBinding("value", 7, java.sql.Types.INTEGER)),
        )
        var suppliedConnection: Connection? = null
        var suppliedQuery: BuiltQuery? = null
        val executor = SchemaExecutor(
            queryExecutor = queryExecutor { supplied, rendered ->
                suppliedConnection = supplied
                suppliedQuery = rendered
                ExecuteResult.Success(true)
            },
            databaseContext = context,
        )
        var result: ExecuteResult<Boolean>? = null

        executor.executeRendered(query) { result = it }

        assertEquals(ExecuteResult.Success(true), result)
        assertSame(connection, suppliedConnection)
        assertSame(query, suppliedQuery)
        assertEquals("CREATE TABLE `sample` (`value` INT DEFAULT :value)", suppliedQuery?.query)
        assertEquals(7, suppliedQuery?.params?.single()?.value)
        assertEquals(1, events.count { it == "close" })
        assertTrue(preparedConnections.isEmpty())
    }

    @Test
    fun `rendered query on an existing scope borrows its connection and does not close it`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val scope = OperationExecutionScope(
            connection,
            DatabasePlatformConfig(DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36)),
        )
        val query = BuiltQuery("CREATE TABLE `borrowed` (`id` INT)", mutableListOf())
        var suppliedConnection: Connection? = null
        var result: ExecuteResult<Boolean>? = null
        val executor = SchemaExecutor(queryExecutor = queryExecutor { supplied, _ ->
            suppliedConnection = supplied
            ExecuteResult.Success(true)
        })

        executor.executeRendered(query, scope, blockExecute = { result = it })

        assertEquals(ExecuteResult.Success(true), result)
        assertSame(connection, suppliedConnection)
        assertEquals(0, events.count { it == "close" })
    }

    @Test
    fun `query info callback receives rendered SQL and no fabricated DDL parameters`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val scope = OperationExecutionScope(
            connection,
            DatabasePlatformConfig(DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36)),
        )
        val queryInfo = mutableListOf<Pair<String?, MutableMap<String, Any?>>>()
        val executor = SchemaExecutor(queryExecutor = queryExecutor { _, _ -> ExecuteResult.Success(true) })

        executor.execute(
            createIndex(),
            scope,
            blockExecute = { },
            blockQueryInfo = { sql, parameters -> queryInfo += sql to parameters },
        )

        assertEquals(1, queryInfo.size)
        assertTrue(queryInfo.single().first.orEmpty().contains("CREATE INDEX"))
        assertTrue(queryInfo.single().second.isEmpty())
        assertEquals(0, events.count { it == "close" })
    }

    @Test
    fun `execution callback failure is latched and rolls back the transaction`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val callbackFailure = IllegalStateException("execution callback failed")
        val executor = SchemaExecutor(
            queryExecutor = queryExecutor { _, _ -> ExecuteResult.Success(true) },
            transactionExecutor = OperationTransactionExecutor { connection },
        )

        val result = executor.transaction { transaction ->
            transaction.execute(createIndex(), blockExecute = { throw callbackFailure })
            ExecuteResult.Success("ignored")
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `expired scope is rejected before the injected executor runs`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val scope = OperationExecutionScope(connection)
        scope.end()
        var executorCalls = 0
        var result: ExecuteResult<Boolean>? = null
        val executor = SchemaExecutor(queryExecutor = queryExecutor { _, _ ->
            executorCalls++
            ExecuteResult.Success(true)
        })

        executor.execute(createIndex(), scope, blockExecute = { result = it })

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, executorCalls)
        assertEquals(0, events.count { it == "close" })
    }

    @Test
    fun `scope rejects execution from a non-owner thread`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val scope = OperationExecutionScope(connection)
        var executorCalls = 0
        var result: ExecuteResult<Boolean>? = null
        val executor = SchemaExecutor(queryExecutor = queryExecutor { _, _ ->
            executorCalls++
            ExecuteResult.Success(true)
        })

        val worker = thread(start = true) {
            executor.execute(createIndex(), scope, blockExecute = { result = it })
        }
        worker.join()

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, executorCalls)
        assertTrue(scope.firstFailure?.message.orEmpty().contains("scope callback thread"))
        assertEquals(0, events.count { it == "close" })
        scope.end()
    }

    @Test
    fun `query executor that omits its callback becomes a latched transaction failure`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executor = SchemaExecutor(
            queryExecutor = queryExecutorWithCallback { _, _, _ -> Unit },
            transactionExecutor = OperationTransactionExecutor { connection },
        )
        var callbackCount = 0

        val result = executor.transaction { transaction ->
            transaction.execute(createIndex(), blockExecute = { callbackCount++ })
            ExecuteResult.Success("ignored")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("without delivering"))
        assertEquals(1, callbackCount)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `duplicate low-level result callbacks fail the transaction`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executor = SchemaExecutor(
            queryExecutor = queryExecutorWithCallback { _, _, callback ->
                callback(ExecuteResult.Success(true))
                callback(ExecuteResult.Success(true))
            },
            transactionExecutor = OperationTransactionExecutor { connection },
        )
        var callbackCount = 0

        val result = executor.transaction { transaction ->
            transaction.execute(createIndex(), blockExecute = { callbackCount++ })
            ExecuteResult.Success("ignored")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("more than one"))
        assertEquals(1, callbackCount)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `query executor error is rethrown after transaction rollback and close`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executionError = AssertionError("fatal query executor error")
        val executor = SchemaExecutor(
            queryExecutor = queryExecutorWithCallback { _, _, _ -> throw executionError },
            transactionExecutor = OperationTransactionExecutor { connection },
        )

        val thrown = assertFailsWith<AssertionError> {
            executor.transaction { transaction ->
                transaction.execute(createIndex(), blockExecute = { })
                ExecuteResult.Success("unreachable")
            }
        }

        assertSame(executionError, thrown)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `connection close failure after standalone success is surfaced with its cause`() {
        val events = mutableListOf<String>()
        val closeFailure = SQLException("connection close failed")
        val connection = connection(events, closeFailure = closeFailure)
        val context = databaseContext(connection)
        val executor = SchemaExecutor(
            queryExecutor = queryExecutor { _, _ -> ExecuteResult.Success(true) },
            databaseContext = context,
        )
        var callbackResult: ExecuteResult<Boolean>? = null

        val thrown = assertFailsWith<IllegalStateException> {
            executor.executeRendered(BuiltQuery("CREATE TABLE `sample` (`id` INT)", mutableListOf())) {
                callbackResult = it
            }
        }

        assertEquals(ExecuteResult.Success(true), callbackResult)
        assertSame(closeFailure, thrown.cause)
        assertEquals(1, events.count { it == "close" })
    }

    private fun queryExecutor(
        executeTable: (Connection, BuiltQuery) -> ExecuteResult<Boolean>,
    ): IQueryExecute = queryExecutorWithCallback { connection, query, callback ->
        callback(executeTable(connection, query))
    }

    private fun queryExecutorWithCallback(
        executeTable: (Connection, BuiltQuery, (ExecuteResult<Boolean>) -> Unit) -> Unit,
    ): IQueryExecute = Proxy.newProxyInstance(
        IQueryExecute::class.java.classLoader,
        arrayOf(IQueryExecute::class.java),
        InvocationHandler { _, method, args ->
            if (method.name == "executeTable" && args?.firstOrNull() is Connection) {
                @Suppress("UNCHECKED_CAST")
                val callback = args[2] as (ExecuteResult<Boolean>) -> Unit
                executeTable(args[0] as Connection, args[1] as BuiltQuery, callback)
                null
            } else {
                throw UnsupportedOperationException("Unexpected QueryExecute method: ${method.name}")
            }
        },
    ) as IQueryExecute

    private fun createIndex() = SchemaCreateIndexBuilder()
        .tableName("users")
        .name("idx_users_email")
        .column("email")

    private fun connection(
        events: MutableList<String>,
        executedConnections: MutableList<Connection>? = null,
        ddlFailure: SQLException? = null,
        closeFailure: SQLException? = null,
    ): Connection {
        var autoCommit = true
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "prepareStatement" -> {
                        executedConnections?.add(proxy as Connection)
                        Proxy.newProxyInstance(
                            PreparedStatement::class.java.classLoader,
                            arrayOf(PreparedStatement::class.java),
                        ) { _, statementMethod, _ ->
                            when (statementMethod.name) {
                                "execute" -> {
                                    ddlFailure?.let { throw it }
                                    true
                                }
                                "close" -> null
                                "isClosed" -> false
                                "toString" -> "ProxyPreparedStatement"
                                else -> when (statementMethod.returnType) {
                                    java.lang.Boolean.TYPE -> false
                                    java.lang.Integer.TYPE -> 0
                                    else -> null
                                }
                            }
                        }
                    }
                    "setAutoCommit" -> {
                        autoCommit = args!![0] as Boolean
                        events += "autoCommit:$autoCommit"
                        null
                    }
                    "getAutoCommit" -> autoCommit
                    "commit", "rollback" -> {
                        events += method.name
                        null
                    }
                    "close" -> {
                        events += "close"
                        closeFailure?.let { throw it }
                        null
                    }
                    "toString" -> "ProxyConnection"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    else -> null
                }
            },
        ) as Connection
    }

    private fun databaseContext(connection: Connection) = DatabaseContext(
        name = "schema-executor-test",
        config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://127.0.0.1",
                dbPort = 3306,
                dbName = "unused-test-context",
                dbUserName = "unused",
                dbPassword = "",
            ),
            platform = DatabasePlatformConfig(DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36)),
        ),
        connectionProvider = object : gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection {
            override fun build(): Connection = connection
        },
    )
}
