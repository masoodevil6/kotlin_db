package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst
import gog.my_project.data_base.migration.ast.schema.drop_column.MigrationDropColumnAst
import java.sql.SQLException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationExecutorDropColumnTest {
    @Test
    fun delegatesRenderedSqlThroughExistingQueryExecutor() {
        val queryExecute = RecordingQueryExecute(ExecuteResult.Success(true))
        val migrationExecutor = MigrationExecutor(queryExecutor = queryExecute)
        val previousConfig = DefaultDatabaseConfig.config
        var receivedResult: ExecuteResult<Boolean>? = null
        var queryInfo: String? = null
        var queryParams: MutableMap<String, Any?>? = null

        try {
            DefaultDatabaseConfig.config = DefaultDatabaseConfig.config.copy(dialect = DialectQuery.MY_SQL)
            migrationExecutor.execute(
                queryBuilder = migrationForTest(),
                blockExecute = { receivedResult = it },
                blockQueryInfo = { query, params ->
                    queryInfo = query
                    queryParams = params
                },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        assertEquals("ALTER TABLE `users` DROP COLUMN `display_name`", queryExecute.executedQuery?.query)
        assertEquals("ALTER TABLE `users` DROP COLUMN `display_name`", queryInfo)
        assertTrue(queryParams.orEmpty().isEmpty())
        assertEquals<ExecuteResult<Boolean>?>(ExecuteResult.Success(true), receivedResult)
    }

    @Test
    fun preservesExecutionFailureFromExistingQueryExecutor() {
        val cause = IllegalStateException("database rejected DDL")
        val queryExecute = RecordingQueryExecute(ExecuteResult.Failure(cause))
        val migrationExecutor = MigrationExecutor(queryExecutor = queryExecute)
        val previousConfig = DefaultDatabaseConfig.config
        var receivedResult: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = DefaultDatabaseConfig.config.copy(dialect = DialectQuery.MY_SQL)
            migrationExecutor.execute(
                queryBuilder = migrationForTest(),
                blockExecute = { receivedResult = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        val failure = receivedResult as ExecuteResult.Failure
        assertSame(cause, failure.exception)
    }

    @Test
    fun ifExistsTreatsMissingColumnAsSuccess() {
        val queryExecute = RecordingQueryExecute(ExecuteResult.Success(true), columnExists = false)
        val result = execute(queryExecute, ifExists = true)
        assertEquals<ExecuteResult<Boolean>?>(ExecuteResult.Success(true), result)
        assertEquals(0, queryExecute.ddlExecutionCount)
        assertTrue(queryExecute.selectQuery?.query?.contains("INFORMATION_SCHEMA.COLUMNS") == true)
        assertEquals(listOf("tableName", "columnName"), queryExecute.selectQuery?.params?.map { it.name })
    }

    @Test
    fun ifExistsExecutesDdlWhenColumnExists() {
        val queryExecute = RecordingQueryExecute(ExecuteResult.Success(true), columnExists = true)
        val result = execute(queryExecute, ifExists = true)
        assertEquals<ExecuteResult<Boolean>?>(ExecuteResult.Success(true), result)
        assertEquals(1, queryExecute.ddlExecutionCount)
    }

    @Test
    fun ifExistsTreatsMissingTableAsSuccess() {
        assertIfExistsSuppresses(1146)
    }

    @Test
    fun ifExistsDoesNotSuppressUnrelatedDatabaseErrors() {
        val cause = SQLException("access denied", "42000", 1044)
        val queryExecute = RecordingQueryExecute(ExecuteResult.Failure(cause))
        val migrationExecutor = MigrationExecutor(queryExecutor = queryExecute)
        val previousConfig = DefaultDatabaseConfig.config
        var receivedResult: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = DefaultDatabaseConfig.config.copy(dialect = DialectQuery.MY_SQL)
            migrationExecutor.execute(
                queryBuilder = migrationForTest(ifExists = true),
                blockExecute = { receivedResult = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        val failure = receivedResult as ExecuteResult.Failure
        assertSame(cause, failure.exception)
    }

    @Test
    fun missingColumnRemainsFailureWhenIfExistsIsDisabled() {
        val cause = SQLException("Can't DROP column", "42000", 1091)
        val queryExecute = RecordingQueryExecute(ExecuteResult.Failure(cause))
        val migrationExecutor = MigrationExecutor(queryExecutor = queryExecute)
        val previousConfig = DefaultDatabaseConfig.config
        var receivedResult: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = DefaultDatabaseConfig.config.copy(dialect = DialectQuery.MY_SQL)
            migrationExecutor.execute(
                queryBuilder = migrationForTest(ifExists = false),
                blockExecute = { receivedResult = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        val failure = receivedResult as ExecuteResult.Failure
        assertSame(cause, failure.exception)
    }

    private fun assertIfExistsSuppresses(mysqlErrorCode: Int) {
        val cause = SQLException("missing drop target", "42000", mysqlErrorCode)
        val queryExecute = RecordingQueryExecute(ExecuteResult.Failure(cause))
        val migrationExecutor = MigrationExecutor(queryExecutor = queryExecute)
        val previousConfig = DefaultDatabaseConfig.config
        var receivedResult: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = DefaultDatabaseConfig.config.copy(dialect = DialectQuery.MY_SQL)
            migrationExecutor.execute(
                queryBuilder = migrationForTest(ifExists = true),
                blockExecute = { receivedResult = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        assertEquals<ExecuteResult<Boolean>?>(ExecuteResult.Success(true), receivedResult)
    }

    private fun execute(queryExecute: RecordingQueryExecute, ifExists: Boolean): ExecuteResult<Boolean>? {
        val migrationExecutor = MigrationExecutor(queryExecutor = queryExecute)
        val previousConfig = DefaultDatabaseConfig.config
        var receivedResult: ExecuteResult<Boolean>? = null
        try {
            DefaultDatabaseConfig.config = DefaultDatabaseConfig.config.copy(dialect = DialectQuery.MY_SQL)
            migrationExecutor.execute(
                queryBuilder = migrationForTest(ifExists),
                blockExecute = { receivedResult = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
        return receivedResult
    }

    private class RecordingQueryExecute(
        private val result: ExecuteResult<Boolean>,
        private val columnExists: Boolean = true,
    ) : IQueryExecute {
        var executedQuery: BuiltQuery? = null
            private set
        var selectQuery: BuiltQuery? = null
            private set
        var ddlExecutionCount = 0
            private set

        override fun executeTable(
            builtQuery: BuiltQuery,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
        ) {
            executedQuery = builtQuery
            ddlExecutionCount++
            blockExecute(result)
        }

        override fun executeSelect(
            builtQuery: BuiltQuery,
            blockExecute: (ExecuteResult<java.sql.ResultSet>) -> Unit,
        ) {
            selectQuery = builtQuery
            val resultSet = Proxy.newProxyInstance(
                java.sql.ResultSet::class.java.classLoader,
                arrayOf(java.sql.ResultSet::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "next" -> columnExists
                    "close" -> null
                    "isClosed" -> false
                    "toString" -> "FakeResultSet"
                    else -> null
                }
            } as java.sql.ResultSet
            blockExecute(ExecuteResult.Success(resultSet))
        }

        override fun executeUpdate(
            builtQuery: BuiltQuery,
            blockExecute: (ExecuteResult<Int>) -> Unit,
        ) = error("Unexpected CRUD execution while testing DROP COLUMN")

        override fun executeInsert(
            builtQuery: BuiltQuery,
            blockExecute: (ExecuteResult<Long>) -> Unit,
        ) = error("Unexpected CRUD execution while testing DROP COLUMN")

        override fun executeDelete(
            builtQuery: BuiltQuery,
            blockExecute: (ExecuteResult<Int>) -> Unit,
        ) = error("Unexpected CRUD execution while testing DROP COLUMN")
    }

    private fun migrationForTest(ifExists: Boolean = false): IMigrationDropColumnApi =
        object : IMigrationDropColumnApi {
            override var ast: IMigrationDropColumnAst = MigrationDropColumnAst().apply {
                tableName = "users"
                name = "display_name"
                this.ifExists = ifExists
            }
            override var params: MutableList<SqlParameter<*>> = mutableListOf()

            override fun tableName(table: String): IMigrationDropColumnApi {
                ast.tableName = table
                return this
            }

            override fun name(name: String): IMigrationDropColumnApi {
                ast.name = name
                return this
            }

            override fun ifExists(): IMigrationDropColumnApi {
                ast.ifExists = true
                return this
            }
        }
}
