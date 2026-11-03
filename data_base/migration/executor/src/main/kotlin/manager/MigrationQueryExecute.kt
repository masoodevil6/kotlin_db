package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DatabaseServerInfo
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.executor.manager.SchemaExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/** Routes Migration's rendered schema SQL through SchemaExecutor and binds all scoped JDBC calls. */
internal class MigrationQueryExecute(
    private val delegate: IQueryExecute,
    private val schemaExecutor: SchemaExecutor,
    private val scope: OperationExecutionScope?,
) : IQueryExecute by delegate {
    override fun executeTable(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        if (scope == null) schemaExecutor.executeRendered(builtQuery, blockExecute)
        else schemaExecutor.executeRendered(builtQuery, scope, blockExecute)
    }

    override fun executeTable(
        connection: Connection,
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        val active = scope
        if (active == null) {
            delegate.executeTable(connection, builtQuery, blockExecute)
        } else {
            withSuppliedConnection(connection, blockExecute) { scoped ->
                schemaExecutor.executeRendered(builtQuery, active, blockExecute)
            }
        }
    }

    override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeSelect(builtQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeSelect(connection, builtQuery, callback)
        }
    }

    override fun executeSelect(connection: Connection, builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeSelect(connection, builtQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeSelect(scoped, builtQuery) { deliver(blockExecute, it) }
        }
    }

    override fun executeSelect(boundQuery: BoundQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeSelect(boundQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeSelect(connection, boundQuery, callback)
        }
    }

    override fun executeSelect(connection: Connection, boundQuery: BoundQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeSelect(connection, boundQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeSelect(scoped, boundQuery) { deliver(blockExecute, it) }
        }
    }

    override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeUpdate(builtQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeUpdate(connection, builtQuery, callback)
        }
    }

    override fun executeUpdate(connection: Connection, builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeUpdate(connection, builtQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeUpdate(scoped, builtQuery) { deliver(blockExecute, it) }
        }
    }

    override fun executeUpdate(boundQuery: BoundQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeUpdate(boundQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeUpdate(connection, boundQuery, callback)
        }
    }

    override fun executeUpdate(connection: Connection, boundQuery: BoundQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeUpdate(connection, boundQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeUpdate(scoped, boundQuery) { deliver(blockExecute, it) }
        }
    }

    override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeInsert(builtQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeInsert(connection, builtQuery, callback)
        }
    }

    override fun executeInsert(connection: Connection, builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeInsert(connection, builtQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeInsert(scoped, builtQuery) { deliver(blockExecute, it) }
        }
    }

    override fun executeInsert(boundQuery: BoundQuery, blockExecute: (ExecuteResult<Long>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeInsert(boundQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeInsert(connection, boundQuery, callback)
        }
    }

    override fun executeInsert(connection: Connection, boundQuery: BoundQuery, blockExecute: (ExecuteResult<Long>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeInsert(connection, boundQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeInsert(scoped, boundQuery) { deliver(blockExecute, it) }
        }
    }

    override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeDelete(builtQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeDelete(connection, builtQuery, callback)
        }
    }

    override fun executeDelete(connection: Connection, builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeDelete(connection, builtQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeDelete(scoped, builtQuery) { deliver(blockExecute, it) }
        }
    }

    override fun executeDelete(boundQuery: BoundQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeDelete(boundQuery, blockExecute)
        else executeOnScope(blockExecute) { connection, callback ->
            delegate.executeDelete(connection, boundQuery, callback)
        }
    }

    override fun executeDelete(connection: Connection, boundQuery: BoundQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
        val active = scope
        if (active == null) delegate.executeDelete(connection, boundQuery, blockExecute)
        else withSuppliedConnection(connection, blockExecute) { scoped ->
            delegate.executeDelete(scoped, boundQuery) { deliver(blockExecute, it) }
        }
    }

    override fun execute(
        builtQuery: BuiltQuery,
        blockExecute: (
            conn: Connection,
            query: String?,
            params: MutableList<out IQueryParameterBinding>,
            paramsName: List<String>,
            error: Throwable?,
        ) -> Unit,
    ) {
        val active = scope
        if (active == null) {
            delegate.execute(builtQuery, blockExecute)
            return
        }
        val rejection = active.rejectOperation()
        if (rejection != null) throw rejection
        val query = builtQuery.getReadyQuery()
        val parameters = builtQuery.params
        val parameterNames = builtQuery.getListParamNames()
        blockExecute(
            active.connection,
            query,
            parameters,
            parameterNames,
            if (query == null) IllegalArgumentException("BuiltQuery query must not be null") else null,
        )
    }

    override fun getDatabaseServerInfo(blockExecute: (ExecuteResult<DatabaseServerInfo>) -> Unit) {
        if (scope == null) {
            delegate.getDatabaseServerInfo(blockExecute)
            return
        }
        executeSelect(BuiltQuery("SELECT VERSION()", mutableListOf())) { result ->
            val parsed: ExecuteResult<DatabaseServerInfo> = when (result) {
                is ExecuteResult.Failure -> result
                is ExecuteResult.Success -> try {
                    val rawVersion = result.result?.use { rows -> if (rows.next()) rows.getString(1) else null }
                    if (rawVersion == null) {
                        ExecuteResult.Failure(IllegalStateException("Database did not return its server version"))
                    } else {
                        ExecuteResult.Success(DatabaseServerInfo.parse(rawVersion))
                    }
                } catch (failure: Throwable) {
                    ExecuteResult.Failure(failure)
                }
            }
            scope.recordResult(parsed)
            blockExecute(parsed)
        }
    }

    private fun <T> executeOnScope(
        blockExecute: (ExecuteResult<T>) -> Unit,
        operation: (Connection, (ExecuteResult<T>) -> Unit) -> Unit,
    ) {
        val active = requireNotNull(scope)
        val rejection = active.rejectOperation()
        if (rejection != null) {
            deliver(blockExecute, ExecuteResult.Failure(rejection))
            return
        }
        operation(active.connection) { deliver(blockExecute, it) }
    }

    private fun <T> withSuppliedConnection(
        supplied: Connection,
        blockExecute: (ExecuteResult<T>) -> Unit,
        operation: (Connection) -> Unit,
    ) {
        val active = requireNotNull(scope)
        val rejection = active.rejectOperation()
        if (rejection != null) {
            deliver(blockExecute, ExecuteResult.Failure(rejection))
            return
        }
        val scoped = active.connection
        if (supplied !== scoped) {
            val failure = active.recordFailure(
                IllegalArgumentException("A Migration transaction cannot execute on a different JDBC Connection"),
            )
            deliver(blockExecute, ExecuteResult.Failure(failure))
            return
        }
        operation(scoped)
    }

    private fun <T> deliver(blockExecute: (ExecuteResult<T>) -> Unit, result: ExecuteResult<T>) {
        scope?.recordResult(result)
        blockExecute(result)
    }
}
