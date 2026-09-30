package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import java.sql.ResultSet

/** Reads and validates persisted migration history. */
internal class SystemMigrationReader(
    private val queryExecutor: IQueryExecute,
) {
    fun read(block: (ExecuteResult<SystemMigrationState>) -> Unit) {
        val queryBuilder = QueryRenderSelectBuilder()
        queryBuilder.select {
            addColumn { column { tableAttribute("id") } }
            addColumn { column { tableAttribute("migration") } }
            addColumn { column { tableAttribute("batch") } }
        }
        queryBuilder.table { table(SystemMigrationTable.NAME, "") }
        queryBuilder.order {
            addColumn { tableAttribute("id") }
            orderAsc()
        }

        queryExecutor.executeSelect(queryBuilder.toBuiltQuery()) { result ->
            when (result) {
                is ExecuteResult.Failure -> block(result)
                is ExecuteResult.Success<*> -> {
                    val rows = result.result as? ResultSet
                    if (rows == null) {
                        block(ExecuteResult.Failure(IllegalStateException("State query returned no ResultSet")))
                    } else {
                        var state: SystemMigrationState? = null
                        var readFailure: Throwable? = null
                        try {
                            state = readHistory(rows)
                        } catch (error: Throwable) {
                            readFailure = error
                        }
                        try {
                            rows.close()
                        } catch (error: Throwable) {
                            if (readFailure == null) readFailure = error else readFailure.addSuppressed(error)
                        }
                        if (readFailure != null) {
                            block(ExecuteResult.Failure(readFailure))
                            return@executeSelect
                        }
                        block(ExecuteResult.Success(requireNotNull(state)))
                    }
                }
            }
        }
    }

    private fun readHistory(rows: ResultSet): SystemMigrationState {
        val history = mutableListOf<AppliedMigration>()
        val seenMigrations = mutableSetOf<String>()
        var previousId = 0
        var currentBatch: Int? = null

        while (rows.next()) {
            val id = rows.getInt("id")
            val migration = rows.getString("migration")
            val batch = rows.getInt("batch")
            require(id > previousId) { "system_migration ids must be positive and strictly increasing" }
            require(!migration.isNullOrBlank()) { "system_migration contains a blank migration identity" }
            require(seenMigrations.add(migration)) { "system_migration contains duplicate identity '$migration'" }
            require(batch > 0) { "system_migration contains a non-positive batch for '$migration'" }

            history += AppliedMigration(id, migration, batch)
            currentBatch = maxOf(currentBatch ?: batch, batch)
            previousId = id
        }

        return SystemMigrationState(history.toList(), currentBatch)
    }
}
