package gog.my_project.data_base.migration.executor.interfaces

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi
import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table.SchemaRenderCreateTableBuilder
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base_schema.builder.ast.drop_column.SchemaDropColumnBuilder
import gog.my_project.data_base_schema.builder.ast.rename_column.SchemaRenameColumnBuilder
import gog.my_project.data_base_schema.renderer.manager.DialectSelector
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test




class MigrationExecutorDispatchTest {
    @Test
    fun defaultExecuteOutRendersSchemaApiAndReturnsItWithTheQueryExecutor() {
        val previousConfig = DefaultDatabaseConfig.config
        val queryExecutor = Proxy.newProxyInstance(
            IQueryExecute::class.java.classLoader,
            arrayOf(IQueryExecute::class.java),
        ) { _, method, _ -> error("executeOut must not execute ${method.name}") } as IQueryExecute
        val executor = RecordingMigrationExecutor().apply {
            queryExecutorValue = queryExecutor
            useDefaultExecuteOut = true
        }
        DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)

        try {
            val operation = SchemaCreateIndexBuilder()
                .tableName("users")
                .name("idx_users_email")
                .column("email")
            var queryInfo: Pair<String?, MutableMap<String, Any?>>? = null
            var executedWith: Pair<IQueryExecute, BuiltQuery>? = null

            executor.executeOut(
                schemaBuilder = operation,
                blockQueryInfo = { query, params -> queryInfo = query to params },
                blockResult = { db, query -> executedWith = db to query },
            )

            val (actualExecutor, builtQuery) = requireNotNull(executedWith)
            val (reportedSql, reportedParams) = requireNotNull(queryInfo)
            assertSame(queryExecutor, actualExecutor)
            assertTrue(builtQuery.query.orEmpty().contains("CREATE INDEX"))
            assertTrue(builtQuery.query.orEmpty().contains("idx_users_email"))
            assertEquals(emptyMap<String, Any?>(), reportedParams)
            assertTrue(reportedSql.orEmpty().contains("CREATE INDEX"))
            assertTrue(reportedSql.orEmpty().contains("idx_users_email"))
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }
    }

    @Test
    fun dispatchesCreateTableToTypedExecutorOverload() {
        val executor = RecordingMigrationExecutor()
        val operation = SchemaRenderCreateTableBuilder().apply { table { tableName("users") } }

        executor.execute(operation, blockExecute = {})

        assertSame(operation, executor.executedOperation)
        assertFalse(executor.genericExecuteOutCalled)
    }

    @Test
    fun dispatchesDropColumnIfExistsToSemanticExecutorOverload() {
        val executor = RecordingMigrationExecutor()
        val operation = SchemaDropColumnBuilder().apply {
            tableName("users")
            name("legacy")
            ifExists()
        }
        var result: ExecuteResult<Boolean>? = null

        executor.execute(operation, blockExecute = { result = it })

        assertSame(operation, executor.executedOperation)
        assertTrue(operation.ast.metadata.ifExists)
        assertEquals(ExecuteResult.Success(true), result)
        assertFalse(executor.genericExecuteOutCalled)
    }

    @Test
    fun dispatchesRenameColumnToTypedExecutorOverload() {
        val executor = RecordingMigrationExecutor()
        val operation = SchemaRenameColumnBuilder().apply {
            tableName("users")
            name("display_name")
            to("full_name")
        }

        executor.execute(operation, blockExecute = {})

        assertSame(operation, executor.executedOperation)
        assertFalse(executor.genericExecuteOutCalled)
    }

    @Test
    fun dispatchesAllFifteenSupportedOperationsToTypedExecutorOverloads() {
        val executor = RecordingMigrationExecutor()
        val operationTypes = listOf(
            ISchemaRenderCreateTableApi::class.java,
            ISchemaDropTableApi::class.java,
            ISchemaRenameTableApi::class.java,
            ISchemaAddColumnApi::class.java,
            ISchemaRenameColumnApi::class.java,
            ISchemaDropColumnApi::class.java,
            ISchemaModifyColumnApi::class.java,
            ISchemaCreateIndexApi::class.java,
            ISchemaDropIndexApi::class.java,
            ISchemaCreateUniqueIndexApi::class.java,
            ISchemaCreateMultiColumnIndexApi::class.java,
            ISchemaCreateFullTextIndexApi::class.java,
            ISchemaCreateSpatialIndexApi::class.java,
            ISchemaCreateForeignKeyApi::class.java,
            ISchemaDropForeignKeyApi::class.java,
        )

        operationTypes.forEach { operationType ->
            val operation = Proxy.newProxyInstance(
                operationType.classLoader,
                arrayOf(operationType),
            ) { _, _, _ -> null } as ISchemaApi<*>

            executor.execute(operation, blockExecute = {})

            assertSame(operation, executor.executedOperation, operationType.simpleName)
            assertFalse(executor.genericExecuteOutCalled, operationType.simpleName)
        }
    }

    @Test
    fun rejectsUnknownSchemaApiBeforeExecution() {
        val executor = RecordingMigrationExecutor()
        val unknown = UnknownSchemaApi()

        val error = assertFailsWith<IllegalArgumentException> {
            executor.execute(unknown, blockExecute = {})
        }

        assertTrue(error.message.orEmpty().contains(UnknownSchemaApi::class.qualifiedName!!))
        assertEquals(null, executor.executedOperation)
        assertFalse(executor.genericExecuteOutCalled)
    }

    @Test
    fun forwardsExecutorFailureUnchanged() {
        val executor = RecordingMigrationExecutor()
        val failure = ExecuteResult.Failure(IllegalStateException("execution failed"))
        executor.resultToSend = failure
        var result: ExecuteResult<Boolean>? = null

        executor.execute(
            SchemaRenderCreateTableBuilder().apply { table { tableName("users") } },
            blockExecute = { result = it },
        )

        assertSame(failure, result)
    }

    private class RecordingMigrationExecutor : IMigrationExecutor {
        var queryExecutorValue: IQueryExecute? = null
        var useDefaultExecuteOut: Boolean = false

        override val queryExecutor: IQueryExecute
            get() = queryExecutorValue ?: error("The test dispatcher must not access IQueryExecute")

        override val dialectSelector: DialectSelector
            get() = if (useDefaultExecuteOut) DialectSelector()
            else error("The test dispatcher must not access DialectSelector")

        var executedOperation: ISchemaApi<*>? = null
        var genericExecuteOutCalled: Boolean = false
        var resultToSend: ExecuteResult<Boolean> = ExecuteResult.Success(true)

        private fun record(
            operation: ISchemaApi<*>,
            callback: (ExecuteResult<Boolean>) -> Unit,
        ) {
            executedOperation = operation
            callback(resultToSend)
        }

        override fun execute(
            queryBuilder: ISchemaRenderCreateTableApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: ISchemaRenameColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: ISchemaDropColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun executeOut(
            schemaBuilder: ISchemaApi<*>,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
            blockResult: (IQueryExecute, BuiltQuery) -> Unit,
        ) {
            if (useDefaultExecuteOut) {
                super<IMigrationExecutor>.executeOut(schemaBuilder, blockQueryInfo, blockResult)
                return
            }
            genericExecuteOutCalled = true
            error("The generic executeOut path must not be used by dispatch")
        }
    }

    private class UnknownSchemaApi : ISchemaApi<UnknownAst> {
        override var ast = UnknownAst()
    }

    private class UnknownAst : ISchemaAst<UnknownAstMetadata> {
        override var metadata = UnknownAstMetadata()
    }

    private class UnknownAstMetadata : gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata
}
