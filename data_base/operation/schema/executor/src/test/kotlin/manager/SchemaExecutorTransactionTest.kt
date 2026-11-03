package gog.my_project.data_base_schema.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class SchemaExecutorTransactionTest {
    @Test
    fun `multiple schema operations share the operation transaction connection`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executedConnections = mutableListOf<Connection>()
        val rawQueryExecutor = queryExecutor { suppliedConnection ->
            executedConnections += suppliedConnection
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
            assertEquals(listOf("autoCommit:false", "commit", "close"), events)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    @Test
    fun `schema failure is latched and rolls back even if callback returns success`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = SQLException("ddl failed")
        val rawQueryExecutor = queryExecutor { ExecuteResult.Failure(failure) }
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
        val rawQueryExecutor = queryExecutor {
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

    private fun queryExecutor(
        executeTable: (Connection) -> ExecuteResult<Boolean>,
    ): IQueryExecute = Proxy.newProxyInstance(
        IQueryExecute::class.java.classLoader,
        arrayOf(IQueryExecute::class.java),
        InvocationHandler { _, method, args ->
            if (method.name == "executeTable" && args?.firstOrNull() is Connection) {
                @Suppress("UNCHECKED_CAST")
                val callback = args[2] as (ExecuteResult<Boolean>) -> Unit
                callback(executeTable(args[0] as Connection))
                null
            } else {
                throw UnsupportedOperationException("Unexpected QueryExecute method: ${method.name}")
            }
        },
    ) as IQueryExecute

    private fun connection(events: MutableList<String>): Connection {
        var autoCommit = true
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "setAutoCommit" -> {
                        autoCommit = args!![0] as Boolean
                        events += "autoCommit:$autoCommit"
                        null
                    }
                    "getAutoCommit" -> autoCommit
                    "commit", "rollback", "close" -> {
                        events += method.name
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
}
