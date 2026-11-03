package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base.migration.executor.manager.MigrationExecutor
import gog.my_project.data_base.migration.executor.manager.MigrationMigrator
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaOperation

/** Consumer entry point for the capabilities configured for one database key. */
class DatabaseHandle internal constructor(
    val key: DatabaseKey,
    private val resolveContext: () -> DatabaseContext,
    private val migrations: MigrationConfiguration?,
) {
    /**
     * Runs the block in a non-transactional Connection Scope. Queries share one Connection and
     * the scoped executor becomes unusable when the block returns.
     */
    fun <T> query(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
        withContext { context -> QueryBuilderExecutor(context).connection(block) }

    /** Runs query work in a Transaction Scope owned by Operation. */
    fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
        withContext { context -> QueryBuilderExecutor(context).transaction(block) }

    /** Executes one Schema operation using the selected database's platform and Connection. */
    fun schema(
        operation: SchemaOperation,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> = withContext { context -> Schema.execute(operation, context, onQueryInfo) }

    /** Runs only the migrations registered for this database. */
    fun migrate(block: (ExecuteResult<Unit>) -> Unit) {
        val context = try {
            resolveContext()
        } catch (error: Error) {
            throw error
        } catch (exception: Exception) {
            block(ExecuteResult.Failure(exception))
            return
        }

        val configuration = migrations
        if (configuration == null) {
            block(
                ExecuteResult.Failure(
                    IllegalStateException("No migration configuration is registered for database '${key::class.qualifiedName}'"),
                ),
            )
            return
        }
        MigrationMigrator(configuration, MigrationExecutor(context)).migrate(block)
    }

    private inline fun <T> withContext(block: (DatabaseContext) -> ExecuteResult<T>): ExecuteResult<T> =
        try {
            block(resolveContext())
        } catch (error: Error) {
            throw error
        } catch (exception: Exception) {
            ExecuteResult.Failure(exception)
        }
}
