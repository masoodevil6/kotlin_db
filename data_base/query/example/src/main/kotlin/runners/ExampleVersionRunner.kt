package gog.my_project.data_base.query.example.runners

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.example.managers.IManagerExample
import gog.my_project.data_base.query.example.managers.ManagerExampleV1
import gog.my_project.data_base.query.example.managers.ManagerExampleV2
import gog.my_project.data_base.query.example.managers.ManagerExampleV3
import gog.my_project.data_base.query.example.managers.ManagerExampleV31
import gog.my_project.data_base.query.example.managers.ManagerExampleV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

data class ExampleRunOptions(
    val runSelect: Boolean,
    val runInsert: Boolean,
    val runUpdate: Boolean,
    val runDelete: Boolean,
    val runTransaction: Boolean,
)

/** Dispatches one selected example version and owns its connection-scope policy. */
object ExampleVersionRunner {
    fun run(
        version: String,
        queryManager: IQueryBuilderExecutor,
        options: ExampleRunOptions,
    ): ExecuteResult<Unit> = when (version) {
        "v1" -> runInConnection(queryManager) { scoped ->
            ManagerExampleV1(
                statusRunSelect = options.runSelect,
                statusRunInsert = options.runInsert,
                statusRunUpdate = options.runUpdate,
                statusRunDelete = options.runDelete,
            ).run(scoped)
        }
        "v2" -> runInConnection(queryManager) { scoped ->
            ManagerExampleV2(
                statusRunSelect = options.runSelect,
                statusRunInsert = options.runInsert,
                statusRunUpdate = options.runUpdate,
                statusRunDelete = options.runDelete,
            ).run(scoped)
        }
        "v3" -> runInConnection(queryManager) { scoped ->
            ManagerExampleV3(
                statusRunSelect = options.runSelect,
                statusRunInsert = options.runInsert,
                statusRunUpdate = options.runUpdate,
                statusRunDelete = options.runDelete,
            ).run(scoped)
        }
        "v3.1" -> runInConnection(queryManager) { scoped ->
            ManagerExampleV31(statusRunSelect = options.runSelect).renderExamples(scoped)
        }
        "v4" -> {
            // V4 examples own transactions; wrapping the whole version in connection{} would
            // attempt unsupported connection/transaction cross-nesting.
            ManagerExampleV4(
                statusRunSelect = options.runSelect,
                statusRunInsert = options.runInsert,
                statusRunUpdate = options.runUpdate,
                statusRunDelete = options.runDelete,
                statusRunTransaction = options.runTransaction,
            ).renderExamples(queryManager)
            ExecuteResult.Success(Unit)
        }
        else -> ExecuteResult.Failure(
            IllegalArgumentException("Unsupported example version '$version'. Choose v1, v2, v3, v3.1, or v4."),
        )
    }

    private fun runInConnection(
        queryManager: IQueryBuilderExecutor,
        runExamples: (scoped: IQueryBuilderExecutor) -> Unit,
    ): ExecuteResult<Unit> = queryManager.connection { scoped ->
        runExamples(scoped)
        ExecuteResult.Success(Unit)
    }

    private fun IManagerExample<*, *, *, *>.run(scoped: IQueryBuilderExecutor) {
        readyListExamples()
        renderExamples(scoped)
    }
}
