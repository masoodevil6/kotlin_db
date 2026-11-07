package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor

/**
 * Concrete handle for the capabilities configured for one database key. Consumers may depend on
 * [DatabaseHandleApi] while existing code can continue using this concrete return type.
 */
class DatabaseHandle private constructor(
    override val key: DatabaseKey,
    private val operationRunner: DatabaseHandleOperationRunner,
) : DatabaseHandleApi {
    /** Internal seam for direct handle lifecycle tests. */
    internal constructor(
        key: DatabaseKey,
        resolveContext: () -> DatabaseContext,
    ) : this(
        key,
        object : DatabaseHandleOperationRunner {
            override fun <T> run(block: (() -> DatabaseContext) -> T): T = block(resolveContext)
        },
    )

    /** Runs the block in a non-transactional Connection Scope owned by Operation. */
    override fun <T> connection(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
        withContext { context -> QueryBuilderExecutor(context).connection(block) }

    /**
     * Runs operations in Operation's Transaction Scope on its managed Connection. Commit,
     * rollback, and cleanup follow the existing Operation transaction contract.
     */
    override fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
        withContext { context -> QueryBuilderExecutor(context).transaction(block) }

    private fun <T> withContext(block: (DatabaseContext) -> ExecuteResult<T>): ExecuteResult<T> =
        try {
            operationRunner.run { resolveContext -> block(resolveContext()) }
        } catch (error: Error) {
            throw error
        } catch (exception: Exception) {
            ExecuteResult.Failure(exception)
        }

    companion object {
        internal fun managed(
            key: DatabaseKey,
            operationRunner: DatabaseHandleOperationRunner,
        ): DatabaseHandle = DatabaseHandle(key, operationRunner)
    }
}

/** Internal bridge so every handle capability shares one Manager admission lease. */
internal interface DatabaseHandleOperationRunner {
    fun <T> run(block: (() -> DatabaseContext) -> T): T
}
