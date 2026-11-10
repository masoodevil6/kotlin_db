package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.MYSQL
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class OperationScopedInjectedDdlTest {
    @Test
    fun `injected DDL executor runs on scope connection without taking its lifecycle`() {
        var closeCount = 0
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "close" -> { closeCount++; null }
                "toString" -> "OperationOwnedConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
        } as Connection
        val scope = OperationExecutionScope(
            connection,
            DatabasePlatformConfig(DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36)),
        )
        val query = BuiltQuery("CREATE TABLE `owned` (`id` INT)", mutableListOf())
        var executorCalls = 0
        var callbackCalls = 0
        val executor = injectedExecutor { suppliedConnection, suppliedQuery, callback ->
            executorCalls++
            assertSame(connection, suppliedConnection)
            assertSame(query, suppliedQuery)
            callbackCalls++
            callback(ExecuteResult.Success(true))
        }

        val result = scope.executeDdl(query, executor)

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(1, executorCalls)
        assertEquals(1, callbackCalls)
        assertEquals(0, closeCount)
        assertEquals(null, scope.firstFailure)
    }

    @Test
    fun `injected DDL failure is latched by the owning scope`() {
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, _, _ -> null } as Connection
        val scope = OperationExecutionScope(
            connection,
            DatabasePlatformConfig(DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36)),
        )
        val failure = SQLException("injected DDL failed")
        val executor = injectedExecutor { _, _, callback -> callback(ExecuteResult.Failure(failure)) }

        val result = scope.executeDdl(BuiltQuery("CREATE TABLE `broken` (`id` INT)", mutableListOf()), executor)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(failure, scope.firstFailure)
    }

    private fun injectedExecutor(
        execute: (Connection, BuiltQuery, (ExecuteResult<Boolean>) -> Unit) -> Unit,
    ): IQueryExecute = Proxy.newProxyInstance(
        IQueryExecute::class.java.classLoader,
        arrayOf(IQueryExecute::class.java),
    ) { _, method, args ->
        if (method.name == "executeTable" && args?.firstOrNull() is Connection) {
            @Suppress("UNCHECKED_CAST")
            val callback = args[2] as (ExecuteResult<Boolean>) -> Unit
            execute(args[0] as Connection, args[1] as BuiltQuery, callback)
            null
        } else {
            throw UnsupportedOperationException("Unexpected IQueryExecute method: ${method.name}")
        }
    } as IQueryExecute
}
