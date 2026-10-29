package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.delete.A1ExampleDeleteV4
import gog.my_project.data_base.query.example.v4.queries.insert.A1ExampleInsertV4
import gog.my_project.data_base.query.example.v4.queries.select.A1ExampleSelectV4
import gog.my_project.data_base.query.example.v4.queries.select.A2ExampleSelectV4
import gog.my_project.data_base.query.example.v4.queries.select.A3ExampleSelectV4
import gog.my_project.data_base.query.example.v4.queries.select.A4ExampleSelectV4
import gog.my_project.data_base.query.example.v4.queries.select.A5ExampleSelectV4
import gog.my_project.data_base.query.example.v4.queries.select.A6ExampleSelectV4
import gog.my_project.data_base.query.example.v4.queries.select.A7ExampleSelectV4
import gog.my_project.data_base.query.example.v4.queries.update.A1ExampleUpdateV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

/** Runs V4 examples by explicit read, mutation, and transaction opt-in flags. */
class ManagerExampleV4(
    private val statusRunSelect: Boolean = true,
    private val statusRunInsert: Boolean = false,
    private val statusRunUpdate: Boolean = false,
    private val statusRunDelete: Boolean = false,
    private val statusRunTransaction: Boolean = false,
) {
    private val selects: List<IExampleV4> = listOf(A1ExampleSelectV4(), A2ExampleSelectV4())
    private val inserts: List<IExampleV4> = listOf(A1ExampleInsertV4())
    private val updates: List<IExampleV4> = listOf(A1ExampleUpdateV4())
    private val deletes: List<IExampleV4> = listOf(A1ExampleDeleteV4())
    private val transactions: List<IExampleV4> = listOf(
        A3ExampleSelectV4(),
        A4ExampleSelectV4(),
        A5ExampleSelectV4(),
        A6ExampleSelectV4(),
        A7ExampleSelectV4(),
    )

    fun renderExamples(queryManager: IQueryBuilderExecutor) {
        if (statusRunSelect) runGroup("select", selects, queryManager)
        if (statusRunInsert) runGroup("insert", inserts, queryManager)
        if (statusRunUpdate) runGroup("update", updates, queryManager)
        if (statusRunDelete) runGroup("delete", deletes, queryManager)
        if (statusRunTransaction) runGroup("transaction", transactions, queryManager)
    }

    private fun runGroup(
        group: String,
        examples: List<IExampleV4>,
        queryManager: IQueryBuilderExecutor,
    ) {
        examples.forEach { example ->
            try {
                println("\n================ V4 $group: ${example::class.simpleName} ================")
                example.execute(queryManager)
            } catch (failure: Exception) {
                println("V4 $group example ${example::class.simpleName} failed: ${failure.message}")
            }
        }
    }
}
