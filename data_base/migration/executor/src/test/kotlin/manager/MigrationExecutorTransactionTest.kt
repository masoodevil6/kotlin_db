package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base.migration.executor.internal.system_migration.SystemMigrationRecorder
import gog.my_project.data_base_schema.renderer.manager.DialectSelector
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class MigrationExecutorTransactionTest {
    @Test
    fun `schema DDL and migration history use the shared transaction connection`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executedConnections = mutableListOf<Connection>()
        val queryExecutor = queryExecutor(executedConnections = executedConnections) { suppliedConnection ->
            executedConnections += suppliedConnection
            ExecuteResult.Success(true)
        }
        val executor = MigrationExecutor(
            queryExecutor,
            DialectSelector(),
            OperationTransactionExecutor { connection },
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

            val result = executor.transaction { tx ->
                tx.execute(first, blockExecute = { })
                tx.execute(second, blockExecute = { })
                var historyResult: ExecuteResult<Unit>? = null
                SystemMigrationRecorder(tx.queryExecutor, tx.databaseContext.platformConfig)
                    .recordSuccessfulMigration("test_migration", 1) {
                    historyResult = it
                }
                assertEquals(ExecuteResult.Success(Unit), historyResult)
                ExecuteResult.Success("done")
            }

            assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
            assertEquals(3, executedConnections.size)
            assertSame(connection, executedConnections[0])
            assertSame(connection, executedConnections[1])
            assertSame(connection, executedConnections[2])
            assertEquals(listOf("autoCommit:false", "commit", "close"), events)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    @Test
    fun `ignored schema failure prevents migration transaction commit`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val connection = connection(events)
        val queryExecutor = queryExecutor { ExecuteResult.Failure(IllegalStateException("DDL failed")) }
        val executor = MigrationExecutor(
            queryExecutor,
            DialectSelector(),
            OperationTransactionExecutor { connection },
        )
        DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)

        try {
            val operation = SchemaCreateIndexBuilder()
                .tableName("users")
                .name("idx_users_email")
                .column("email")

            val result = executor.transaction { tx ->
                tx.execute(operation, blockExecute = { /* deliberately ignored */ })
                ExecuteResult.Success("ignored")
            }

            assertIs<ExecuteResult.Failure>(result)
            assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    @Test
    fun `migration history query failure rolls back schema DDL`() {
        val previousConfig = DefaultDatabaseConfig.config
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executedConnections = mutableListOf<Connection>()
        val queryFailure = IllegalStateException("history insert failed")
        val queryExecutor = queryExecutor(
            executedConnections = executedConnections,
            executeUpdate = { ExecuteResult.Failure(queryFailure) },
        ) { suppliedConnection ->
            executedConnections += suppliedConnection
            ExecuteResult.Success(true)
        }
        val executor = MigrationExecutor(
            queryExecutor,
            DialectSelector(),
            OperationTransactionExecutor { connection },
        )
        DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)

        try {
            val result = executor.transaction { tx ->
                val operation = SchemaCreateIndexBuilder()
                    .tableName("users")
                    .name("idx_users_email")
                    .column("email")
                tx.execute(operation, blockExecute = { })

                var historyResult: ExecuteResult<Unit>? = null
                SystemMigrationRecorder(tx.queryExecutor, tx.databaseContext.platformConfig)
                    .recordSuccessfulMigration("test_migration", 1) {
                    historyResult = it
                }
                assertSame(queryFailure, assertIs<ExecuteResult.Failure>(historyResult).exception)
                ExecuteResult.Success("must roll back")
            }

            assertSame(queryFailure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(2, executedConnections.size)
            assertSame(connection, executedConnections[0])
            assertSame(connection, executedConnections[1])
            assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    private fun queryExecutor(
        executedConnections: MutableList<Connection>? = null,
        executeUpdate: (Connection) -> ExecuteResult<Int> = { ExecuteResult.Success(1) },
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
            } else if (method.name == "executeUpdate" && args?.firstOrNull() is Connection) {
                val suppliedConnection = args[0] as Connection
                executedConnections?.add(suppliedConnection)
                @Suppress("UNCHECKED_CAST")
                val callback = args[2] as (ExecuteResult<Int>) -> Unit
                callback(executeUpdate(suppliedConnection))
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
