package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.data_base.DatabaseServerInfo
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.data_base.MARIA_DB
import gog.my_project.data_base.core.data_base.MYSQL
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst
import gog.my_project.data_base.migration.ast.schema.rename_column.MigrationRenameColumnAst
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import java.lang.reflect.Proxy
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MigrationExecutorRenameColumnTest {
    @Test
    fun delegatesToExistingManagerAndPreservesResultAndError() {
        val failure = IllegalStateException("database refused rename")
        val db = RecordingQueryExecute()
        val executor = MigrationExecutor(queryExecutor = db)
        val migration = object : IMigrationRenameColumnApi {
            override var ast: IMigrationRenameColumnAst = MigrationRenameColumnAst().apply {
                tableName = "users"; name = "display_name"; to = "full_name"
            }
            override var params: MutableList<SqlParameter<*>> = mutableListOf()
            override fun tableName(table: String) = apply { ast.tableName = table }
            override fun name(name: String) = apply { ast.name = name }
            override fun to(name: String) = apply { ast.to = name }
        }
        var received: ExecuteResult<Boolean>? = null
        var reported: String? = null
        db.result = ExecuteResult.Success(true)
        executor.execute(migration, { received = it }, { sql, _ -> reported = sql })
        assertEquals("ALTER TABLE `users` RENAME COLUMN `display_name` TO `full_name`", db.query?.query)
        assertEquals(db.query?.query, reported)
        assertEquals(ExecuteResult.Success(true), received)
        db.result = ExecuteResult.Failure(failure)
        executor.execute(migration, { received = it })
        assertSame(failure, (received as ExecuteResult.Failure).exception)
    }

    @Test
    fun rejectsUnsupportedServerWithoutRewritingSql() {
        val db = RecordingQueryExecute(serverVersion = "10.4.28-MariaDB")
        val executor = MigrationExecutor(queryExecutor = db)
        var received: ExecuteResult<Boolean>? = null

        executor.execute(
            queryBuilder = migrationForTest(),
            blockExecute = { received = it },
        )

        assertEquals(null, db.query)
        assertEquals(null, db.selectQuery)
        assert(received is ExecuteResult.Failure)
    }

    @Test
    fun targetLegacyStrategyRemainsLegacyOnNewerDetectedServerAndPreservesDefinition() {
        val oldConfig = DefaultDatabaseConfig.config
        val db = RecordingQueryExecute(
            serverVersion = "10.5.3-MariaDB",
            createTableSql = """CREATE TABLE `users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `display_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'New user, here' COMMENT 'display (name)',
  `total` decimal(10,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB""",
        )
        var received: ExecuteResult<Boolean>? = null
        try {
            DefaultDatabaseConfig.config = oldConfig.copy(
                dialect = DialectQuery.MARIA_DB,
                targetDatabaseVersion = MARIA_DB.Version(10, 4, 28),
            )
            MigrationExecutor(queryExecutor = db).execute(
                queryBuilder = migrationForTest(),
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = oldConfig
        }

        assertEquals("SHOW CREATE TABLE `users`", db.selectQuery?.query)
        assertEquals(
            "ALTER TABLE `users` CHANGE COLUMN `display_name` `full_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'New user, here' COMMENT 'display (name)'",
            db.query?.query,
        )
        assertEquals(ExecuteResult.Success(true), received)
    }

    @Test
    fun nativeTargetAgainstOlderDetectedServerFailsBeforeDefinitionLookup() {
        val oldConfig = DefaultDatabaseConfig.config
        val db = RecordingQueryExecute(serverVersion = "10.4.28-MariaDB")
        var received: ExecuteResult<Boolean>? = null
        try {
            DefaultDatabaseConfig.config = oldConfig.copy(
                dialect = DialectQuery.MARIA_DB,
                targetDatabaseVersion = MARIA_DB.Version(10, 5, 3),
            )
            MigrationExecutor(queryExecutor = db).execute(
                queryBuilder = migrationForTest(),
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = oldConfig
        }

        assertEquals(null, db.selectQuery)
        assertEquals(null, db.query)
        assert(received is ExecuteResult.Failure)
    }

    @Test
    fun rejectsDetectedProductMismatchBeforeDdl() {
        val oldConfig = DefaultDatabaseConfig.config
        val db = RecordingQueryExecute(serverVersion = "10.6.18-MariaDB")
        var received: ExecuteResult<Boolean>? = null
        try {
            DefaultDatabaseConfig.config = oldConfig.copy(targetDatabaseVersion = MYSQL.Version(8, 0, 36))
            MigrationExecutor(queryExecutor = db).execute(
                queryBuilder = migrationForTest(),
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = oldConfig
        }

        assertEquals(null, db.query)
        assert(received is ExecuteResult.Failure)
    }

    private fun migrationForTest() = object : IMigrationRenameColumnApi {
        override var ast: IMigrationRenameColumnAst = MigrationRenameColumnAst().apply {
            tableName = "users"; name = "display_name"; to = "full_name"
        }
        override var params: MutableList<SqlParameter<*>> = mutableListOf()
        override fun tableName(table: String) = apply { ast.tableName = table }
        override fun name(name: String) = apply { ast.name = name }
        override fun to(name: String) = apply { ast.to = name }
    }

    private class RecordingQueryExecute(
        private val serverVersion: String = "8.0.36",
        private val createTableSql: String = "CREATE TABLE `users` (`display_name` varchar(120) NOT NULL)",
    ) : IQueryExecute {
        var query: BuiltQuery? = null
        var selectQuery: BuiltQuery? = null
        var result: ExecuteResult<Boolean> = ExecuteResult.Success(true)
        override fun getDatabaseServerInfo(blockExecute: (ExecuteResult<DatabaseServerInfo>) -> Unit) {
            blockExecute(ExecuteResult.Success(DatabaseServerInfo.parse(serverVersion)))
        }

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            query = builtQuery
            blockExecute(result)
        }
        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
            selectQuery = builtQuery
            var first = true
            val resultSet = Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "next" -> first.also { first = false }
                    "getString" -> if (args?.firstOrNull() == 2) createTableSql else null
                    "close" -> null
                    "isClosed" -> false
                    "toString" -> "FakeCreateTableResultSet"
                    else -> null
                }
            } as ResultSet
            blockExecute(ExecuteResult.Success(resultSet))
        }
        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected UPDATE")
        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected INSERT")
        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected DELETE")
    }
}
