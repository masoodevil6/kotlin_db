package gog.my_project.data_base.migration.executor.interfaces

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi
import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.builder.dropColumn
import gog.my_project.data_base.migration.builder.renameColumn
import gog.my_project.data_base.migration.renderer.manager.DialectSelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.lang.reflect.Proxy

class MigrationExecutorDispatchTest {
    @Test
    fun dispatchesCreateTableToTypedExecutorOverload() {
        val executor = RecordingMigrationExecutor()
        val operation = createTable { table { tableName("users") } }

        executor.execute(operation, blockExecute = {})

        assertSame(operation, executor.executedOperation)
        assertFalse(executor.genericExecuteOutCalled)
    }

    @Test
    fun dispatchesDropColumnIfExistsToSemanticExecutorOverload() {
        val executor = RecordingMigrationExecutor()
        val operation = dropColumn {
            tableName("users")
            name("legacy")
            ifExists()
        }
        var result: ExecuteResult<Boolean>? = null

        executor.execute(operation, blockExecute = { result = it })

        assertSame(operation, executor.executedOperation)
        assertTrue(operation.ast.ifExists)
        assertEquals(ExecuteResult.Success(true), result)
        assertFalse(executor.genericExecuteOutCalled)
    }

    @Test
    fun dispatchesRenameColumnToTypedExecutorOverload() {
        val executor = RecordingMigrationExecutor()
        val operation = renameColumn {
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
            IMigrationRenderCreateTableApi::class.java,
            IMigrationDropTableApi::class.java,
            IMigrationRenameTableApi::class.java,
            IMigrationAddColumnApi::class.java,
            IMigrationRenameColumnApi::class.java,
            IMigrationDropColumnApi::class.java,
            IMigrationModifyColumnApi::class.java,
            IMigrationCreateIndexApi::class.java,
            IMigrationDropIndexApi::class.java,
            IMigrationCreateUniqueIndexApi::class.java,
            IMigrationCreateMultiColumnIndexApi::class.java,
            IMigrationCreateFullTextIndexApi::class.java,
            IMigrationCreateSpatialIndexApi::class.java,
            IMigrationCreateForeignKeyApi::class.java,
            IMigrationDropForeignKeyApi::class.java,
        )

        operationTypes.forEach { operationType ->
            val operation = Proxy.newProxyInstance(
                operationType.classLoader,
                arrayOf(operationType),
            ) { _, _, _ -> null } as IMigrationApi<*>

            executor.execute(operation, blockExecute = {})

            assertSame(operation, executor.executedOperation, operationType.simpleName)
            assertFalse(executor.genericExecuteOutCalled, operationType.simpleName)
        }
    }

    @Test
    fun rejectsUnknownMigrationApiBeforeExecution() {
        val executor = RecordingMigrationExecutor()
        val unknown = UnknownMigrationApi()

        val error = assertFailsWith<IllegalArgumentException> {
            executor.execute(unknown, blockExecute = {})
        }

        assertTrue(error.message.orEmpty().contains(UnknownMigrationApi::class.qualifiedName!!))
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
            createTable { table { tableName("users") } },
            blockExecute = { result = it },
        )

        assertSame(failure, result)
    }

    private class RecordingMigrationExecutor : IMigrationExecutor {
        override val queryExecutor: IQueryExecute
            get() = error("The test dispatcher must not access IQueryExecute")

        override val dialectSelector: DialectSelector
            get() = error("The test dispatcher must not access DialectSelector")

        var executedOperation: IMigrationApi<*>? = null
        var genericExecuteOutCalled: Boolean = false
        var resultToSend: ExecuteResult<Boolean> = ExecuteResult.Success(true)

        private fun record(
            operation: IMigrationApi<*>,
            callback: (ExecuteResult<Boolean>) -> Unit,
        ) {
            executedOperation = operation
            callback(resultToSend)
        }

        override fun execute(
            queryBuilder: IMigrationRenderCreateTableApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: IMigrationRenameColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: IMigrationDropColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun execute(
            queryBuilder: gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi,
            blockExecute: (ExecuteResult<Boolean>) -> Unit,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
        ) = record(queryBuilder, blockExecute)

        override fun <Ast : IMigrationAst, Api : IMigrationApi<Ast>> executeOut(
            migrationBuilder: Api,
            blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
            blockResult: (IQueryExecute, BuiltQuery) -> Unit,
        ) {
            genericExecuteOutCalled = true
            error("The generic executeOut path must not be used by dispatch")
        }
    }

    private class UnknownMigrationApi : IMigrationApi<UnknownAst> {
        override var ast = UnknownAst()
        override var params: MutableList<SqlParameter<*>> = mutableListOf()
    }

    private class UnknownAst : IMigrationAst
}
