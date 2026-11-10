package gog.my_project.data_base_query.executor.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base_query.executor.result.QueryRow
import gog.my_project.data_base_query.executor.result.QueryRowMaterializer
import java.sql.Connection
import java.sql.ResultSet

/** Rendered SQL plus its original binding contract, ready for one execution backend. */
internal sealed interface QueryExecutionStatement {
    data class Built(val query: BuiltQuery) : QueryExecutionStatement
    data class Bound(val query: BoundQuery) : QueryExecutionStatement
}

/** Internal execution boundary. Rendering and consumers remain shared by QueryBuilderExecutor. */
internal interface QueryExecutionBackend {
    val databasePlatformConfig: DatabasePlatformConfig

    fun executeSelect(
        statement: QueryExecutionStatement,
        callback: (ExecuteResult<ResultSet>) -> Unit,
    )

    fun selectRows(
        statement: QueryExecutionStatement,
        outputColumns: List<IQueryColumnsAst>,
        firstOnly: Boolean,
        callback: (ExecuteResult<List<QueryRow>>) -> Unit,
    )

    fun executeInsert(statement: QueryExecutionStatement, callback: (ExecuteResult<Long>) -> Unit)
    fun executeUpdate(statement: QueryExecutionStatement, callback: (ExecuteResult<Int>) -> Unit)
    fun executeDelete(statement: QueryExecutionStatement, callback: (ExecuteResult<Int>) -> Unit)

    fun rejectLifecycle(operation: String): ExecuteResult.Failure
    fun recordConsumerThrowable(failure: Throwable)
}

/**
 * Runs standard Root Query statements through Operation. Raw ResultSet callbacks remain a
 * compatibility escape hatch because Operation deliberately does not expose JDBC ResultSet.
 */
internal class RootQueryExecutionBackend(
    private val operationExecutionExecutor: OperationExecutionExecutor,
    private val databaseContext: DatabaseContext,
) : QueryExecutionBackend {
    override val databasePlatformConfig: DatabasePlatformConfig
        get() = databaseContext.platformConfig

    override fun executeSelect(
        statement: QueryExecutionStatement,
        callback: (ExecuteResult<ResultSet>) -> Unit,
    ) {
        // Compatibility path for IQueryBuilderExecutor.execute(ResultSet callback).
        // Standard fluent SELECT uses selectRows() and Operation's typed SELECT API.
        val executor = QueryExecute(databaseContext)
        when (statement) {
            is QueryExecutionStatement.Built -> executor.executeSelect(statement.query, callback)
            is QueryExecutionStatement.Bound -> executor.executeSelect(statement.query, callback)
        }
    }

    /** Materializes QueryRows inside Operation's synchronous row callback. */
    override fun selectRows(
        statement: QueryExecutionStatement,
        outputColumns: List<IQueryColumnsAst>,
        firstOnly: Boolean,
        callback: (ExecuteResult<List<QueryRow>>) -> Unit,
    ) = executeRootWithCallback(
        execute = { scope ->
            var rowsResult: ExecuteResult<List<QueryRow>>? = null
            OperationScopeQueryExecutionBackend(scope).selectRows(
                statement,
                outputColumns,
                firstOnly,
            ) { rows -> rowsResult = rows }
            rowsResult ?: ExecuteResult.Failure(
                IllegalStateException("Scoped SELECT did not produce a materialized result"),
            )
        },
        callback = callback,
    )

    override fun executeInsert(statement: QueryExecutionStatement, callback: (ExecuteResult<Long>) -> Unit) =
        executeRootWithCallback(execute = { scope ->
            when (statement) {
                is QueryExecutionStatement.Built -> scope.executeInsert(statement.query)
                is QueryExecutionStatement.Bound -> scope.executeInsert(statement.query)
            }
        }, callback = callback)

    override fun executeUpdate(statement: QueryExecutionStatement, callback: (ExecuteResult<Int>) -> Unit) =
        executeRootWithCallback(execute = { scope ->
            when (statement) {
                is QueryExecutionStatement.Built -> scope.executeUpdate(statement.query)
                is QueryExecutionStatement.Bound -> scope.executeUpdate(statement.query)
            }
        }, callback = callback)

    override fun executeDelete(statement: QueryExecutionStatement, callback: (ExecuteResult<Int>) -> Unit) =
        executeRootWithCallback(execute = { scope ->
            when (statement) {
                is QueryExecutionStatement.Built -> scope.executeDelete(statement.query)
                is QueryExecutionStatement.Bound -> scope.executeDelete(statement.query)
            }
        }, callback = callback)

    override fun rejectLifecycle(operation: String): ExecuteResult.Failure = ExecuteResult.Failure(
        UnsupportedOperationException("Root lifecycle rejection is only available in Scope-bound mode: $operation"),
    )

    override fun recordConsumerThrowable(failure: Throwable) = Unit

    /**
     * Delivers results while the Operation scope is active. If user code throws, Operation records
     * it before cleanup; rethrow the latched primary failure afterward to preserve callback behavior.
     */
    private fun <T> executeRootWithCallback(
        execute: (OperationExecutionContext) -> ExecuteResult<T>,
        callback: (ExecuteResult<T>) -> Unit,
    ) {
        var delivered = false
        var deliveredResult: ExecuteResult<T>? = null
        var callbackFailure: Throwable? = null
        fun deliver(result: ExecuteResult<T>) {
            check(!delivered) { "Query execution delivered more than one result" }
            delivered = true
            deliveredResult = result
            try {
                callback(result)
            } catch (failure: Throwable) {
                callbackFailure = failure
                throw failure
            }
        }

        val outcome = operationExecutionExecutor.connection { scope ->
            val result = execute(scope)
            deliver(result)
            result
        }

        if (!delivered) deliver(outcome)
        callbackFailure?.let { thrown ->
            val primary = (outcome as? ExecuteResult.Failure)?.exception ?: thrown
            if (primary !== thrown && primary.suppressed.none { it === thrown }) {
                primary.addSuppressed(thrown)
            }
            throw primary
        }

        // A successful result must be delivered while the Operation scope is active, before
        // cleanup. If cleanup subsequently fails, surface that final Operation failure through
        // the synchronous caller instead of invoking the callback a second time.
        if (deliveredResult is ExecuteResult.Success && outcome is ExecuteResult.Failure) {
            throw outcome.exception
        }
    }
}

/** Executes through the caller-owned Operation context and never acquires or owns JDBC resources. */
internal class OperationScopeQueryExecutionBackend(
    private val scope: OperationExecutionContext,
    private val compatibilityConnection: (() -> Connection?)? = null,
) : QueryExecutionBackend {
    override val databasePlatformConfig: DatabasePlatformConfig
        get() = scope.databasePlatformConfig

    override fun executeSelect(
        statement: QueryExecutionStatement,
        callback: (ExecuteResult<ResultSet>) -> Unit,
    ) {
        compatibilityConnection?.invoke()?.let { connection ->
            // Preserve the old low-level callback contract for concrete managed scopes.
            val executor = QueryExecute(connection)
            when (statement) {
                is QueryExecutionStatement.Built -> executor.executeSelect(statement.query, callback)
                is QueryExecutionStatement.Bound -> executor.executeSelect(statement.query, callback)
            }
            return
        }
        val unsupported = UnsupportedOperationException(
            "Raw ResultSet SELECT callbacks are unavailable in Scope-bound mode; use first(), get(), or QueryConsumer",
        )
        val recorded = scope.failed<ResultSet>(unsupported)
        callback(recorded as? ExecuteResult.Failure ?: ExecuteResult.Failure(unsupported))
    }

    override fun selectRows(
        statement: QueryExecutionStatement,
        outputColumns: List<IQueryColumnsAst>,
        firstOnly: Boolean,
        callback: (ExecuteResult<List<QueryRow>>) -> Unit,
    ) = callback(
        when (statement) {
            is QueryExecutionStatement.Built -> scope.executeSelect(statement.query) { rows ->
                readRows(rows, outputColumns, firstOnly)
            }
            is QueryExecutionStatement.Bound -> scope.executeSelect(statement.query) { rows ->
                readRows(rows, outputColumns, firstOnly)
            }
        },
    )

    override fun executeInsert(statement: QueryExecutionStatement, callback: (ExecuteResult<Long>) -> Unit) {
        callback(
            when (statement) {
                is QueryExecutionStatement.Built -> scope.executeInsert(statement.query)
                is QueryExecutionStatement.Bound -> scope.executeInsert(statement.query)
            },
        )
    }

    override fun executeUpdate(statement: QueryExecutionStatement, callback: (ExecuteResult<Int>) -> Unit) {
        callback(
            when (statement) {
                is QueryExecutionStatement.Built -> scope.executeUpdate(statement.query)
                is QueryExecutionStatement.Bound -> scope.executeUpdate(statement.query)
            },
        )
    }

    override fun executeDelete(statement: QueryExecutionStatement, callback: (ExecuteResult<Int>) -> Unit) {
        callback(
            when (statement) {
                is QueryExecutionStatement.Built -> scope.executeDelete(statement.query)
                is QueryExecutionStatement.Bound -> scope.executeDelete(statement.query)
            },
        )
    }

    override fun rejectLifecycle(operation: String): ExecuteResult.Failure {
        val failure = UnsupportedOperationException("$operation is unavailable on a Scope-bound Query Executor")
        return scope.failed<Any?>(failure) as? ExecuteResult.Failure ?: ExecuteResult.Failure(failure)
    }

    override fun recordConsumerThrowable(failure: Throwable) {
        scope.failed<Any?>(failure)
    }

    private fun readRows(
        rows: OperationQueryRows,
        outputColumns: List<IQueryColumnsAst>,
        firstOnly: Boolean,
    ): List<QueryRow> {
        val result = ArrayList<QueryRow>()
        while (rows.next()) {
            result += QueryRowMaterializer.materialize(rows, outputColumns)
            if (firstOnly) break
        }
        return result
    }
}
