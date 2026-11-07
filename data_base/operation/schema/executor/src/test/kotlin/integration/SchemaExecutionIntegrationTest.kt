package gog.my_project.data_base_schema.executor.integration

import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.MARIA_DB
import gog.my_project.data_base_operation.config.data_base.MYSQL
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base_schema.builder.ast.drop_column.SchemaDropColumnBuilder
import gog.my_project.data_base_schema.builder.ast.rename_column.SchemaRenameColumnBuilder
import gog.my_project.data_base_schema.executor.manager.SchemaExecutor
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SchemaExecutionIntegrationTest {
    @Test
    fun `ordinary DDL is rendered once reported and sent directly to the supplied scope`() {
        val jdbc = FakeJdbc()
        val scope = scope(jdbc, DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36))
        val queryInfo = mutableListOf<Pair<String, Map<String, Any?>>>()

        val result = SchemaExecutionIntegration.executeScoped(createIndex(), scope) { sql, parameters ->
            queryInfo += sql to parameters
        }

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(listOf("CREATE INDEX `idx_users_email` ON `users` (`email`)"), jdbc.sql)
        assertEquals(1, queryInfo.size)
        assertTrue(queryInfo.single().first.contains("CREATE INDEX"))
        assertTrue(queryInfo.single().second.isEmpty())
        assertEquals(0, jdbc.connectionCloseCount)
    }

    @Test
    fun `native rename executes only renderer output without server version reads`() {
        val jdbc = FakeJdbc()
        val scope = scope(jdbc, DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36))

        val result = SchemaExecutionIntegration.executeScoped(renameColumn(), scope)

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(
            listOf("ALTER TABLE `users` RENAME COLUMN `display_name` TO `full_name`"),
            jdbc.sql,
        )
        assertEquals(0, jdbc.connectionCloseCount)
    }

    @Test
    fun `legacy rename strategy failure is returned without metadata introspection`() {
        val jdbc = FakeJdbc()
        val scope = scope(jdbc, DialectQuery.MARIA_DB, MARIA_DB.Version(10, 4, 28))

        val result = SchemaExecutionIntegration.executeScoped(renameColumn(), scope)

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("CHANGE COLUMN"))
        assertTrue(jdbc.sql.isEmpty())
        assertSame(failure, scope.firstFailure)
    }

    @Test
    fun `drop column if exists sends renderer output directly for MariaDB`() {
        val jdbc = FakeJdbc()
        val scope = scope(jdbc, DialectQuery.MARIA_DB, MARIA_DB.Version(10, 4, 28))

        val result = SchemaExecutionIntegration.executeScoped(dropColumn(ifExists = true), scope)

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(listOf("ALTER TABLE `users` DROP COLUMN IF EXISTS `display_name`"), jdbc.sql)
    }

    @Test
    fun `unsupported MySQL drop column if exists is returned without preflight query`() {
        val jdbc = FakeJdbc()
        val scope = scope(jdbc, DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36))

        val result = SchemaExecutionIntegration.executeScoped(dropColumn(ifExists = true), scope)

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure is UnsupportedOperationException)
        assertTrue(jdbc.sql.isEmpty())
        assertSame(failure, scope.firstFailure)
    }

    @Test
    fun `database DDL failure is passed through and latched rather than converted to success`() {
        val failure = SQLException("DDL rejected by database", "42000", 1044)
        val jdbc = FakeJdbc(ddlFailure = failure)
        val scope = scope(jdbc, DialectQuery.MARIA_DB, MARIA_DB.Version(10, 4, 28))

        val result = SchemaExecutionIntegration.executeScoped(dropColumn(ifExists = true), scope)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("ALTER TABLE `users` DROP COLUMN IF EXISTS `display_name`"), jdbc.sql)
        assertSame(failure, scope.firstFailure)
    }

    @Test
    fun `query info callback failure is latched and prevents DDL execution`() {
        val jdbc = FakeJdbc()
        val scope = scope(jdbc, DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36))
        val failure = IllegalStateException("query info callback failed")

        val result = SchemaExecutionIntegration.executeScoped(createIndex(), scope) { _, _ -> throw failure }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(jdbc.sql.isEmpty())
        assertSame(failure, scope.firstFailure)
    }

    @Test
    fun `prior scope failure prevents DDL execution`() {
        val jdbc = FakeJdbc()
        val scope = scope(jdbc, DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36))
        val prior = IllegalStateException("earlier operation failed")
        scope.failed<Boolean>(prior)

        val result = SchemaExecutionIntegration.executeScoped(createIndex(), scope)

        assertSame(prior, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(jdbc.sql.isEmpty())
    }

    @Test
    fun `independent executor owns and closes its connection once`() {
        val jdbc = FakeJdbc()
        val context = DatabaseContext(
            name = "schema-test",
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
            connectionProvider = object : IDatabaseConnection {
                override fun build(): Connection = jdbc.connection
            },
        )
        var result: ExecuteResult<Boolean>? = null

        SchemaExecutor(databaseContext = context).execute(createIndex(), blockExecute = { result = it })

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(listOf("CREATE INDEX `idx_users_email` ON `users` (`email`)"), jdbc.sql)
        assertEquals(1, jdbc.connectionCloseCount)
    }

    private fun scope(jdbc: FakeJdbc, dialect: DialectQuery, version: gog.my_project.data_base_operation.config.data_base.ProductDatabaseVersion) =
        OperationExecutionScope(jdbc.connection, DatabasePlatformConfig(dialect, version))

    private fun createIndex() = SchemaCreateIndexBuilder()
        .tableName("users")
        .name("idx_users_email")
        .column("email")

    private fun renameColumn() = SchemaRenameColumnBuilder()
        .tableName("users")
        .name("display_name")
        .to("full_name")

    private fun dropColumn(ifExists: Boolean) = SchemaDropColumnBuilder()
        .tableName("users")
        .name("display_name")
        .apply { if (ifExists) ifExists() }

    private class FakeJdbc(private val ddlFailure: SQLException? = null) {
        val sql = mutableListOf<String>()
        var connectionCloseCount = 0
            private set

        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val statementSql = args!![0] as String
                    sql += statementSql
                    preparedStatement()
                }
                "close" -> {
                    connectionCloseCount++
                    null
                }
                "isClosed" -> connectionCloseCount > 0
                "toString" -> "FakeSchemaConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun preparedStatement(): PreparedStatement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "execute" -> {
                    ddlFailure?.let { throw it }
                    true
                }
                "executeUpdate" -> {
                    ddlFailure?.let { throw it }
                    1
                }
                "getResultSetType" -> java.sql.ResultSet.TYPE_FORWARD_ONLY
                "isClosed" -> false
                "close" -> null
                "toString" -> "FakeSchemaPreparedStatement"
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0.0f
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
