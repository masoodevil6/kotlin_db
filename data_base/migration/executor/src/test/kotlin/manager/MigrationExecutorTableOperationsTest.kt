package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.builder.ast.drop_table.SchemaDropTableBuilder
import gog.my_project.data_base_schema.builder.ast.rename_table.SchemaRenameTableBuilder
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationExecutorTableOperationsTest {
    @Test
    fun `drop table maps and executes through operation schema`() {
        val previousConfig = DefaultDatabaseConfig.config
        val (executor, queries) = recordingExecutor()
        var result: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                SchemaDropTableBuilder().apply { tableName("users"); ifExists() },
                blockExecute = { result = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        assertEquals("DROP TABLE IF EXISTS `users`", queries.single().query)
        assertEquals(ExecuteResult.Success(true), result)
    }

    @Test
    fun `rename table maps and executes through operation schema`() {
        val previousConfig = DefaultDatabaseConfig.config
        val (executor, queries) = recordingExecutor()
        var result: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                SchemaRenameTableBuilder().apply {
                    fromTableName("users_old")
                    toTableName("users")
                },
                blockExecute = { result = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        assertEquals("RENAME TABLE `users_old` TO `users`", queries.single().query)
        assertEquals(ExecuteResult.Success(true), result)
    }

    private fun recordingExecutor(): Pair<MigrationExecutor, MutableList<BuiltQuery>> {
        val queries = mutableListOf<BuiltQuery>()
        val queryExecutor = Proxy.newProxyInstance(
            IQueryExecute::class.java.classLoader,
            arrayOf(IQueryExecute::class.java),
        ) { _, method, args ->
            if (method.name == "executeTable" && args?.firstOrNull() is BuiltQuery) {
                queries += args[0] as BuiltQuery
                @Suppress("UNCHECKED_CAST")
                val callback = args[1] as (ExecuteResult<Boolean>) -> Unit
                callback(ExecuteResult.Success(true))
                null
            } else {
                throw UnsupportedOperationException("Unexpected QueryExecute method: ${method.name}")
            }
        } as IQueryExecute
        return MigrationExecutor(queryExecutor) to queries
    }
}
