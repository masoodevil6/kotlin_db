package gog.my_project.data_base.query.example.v3_1.queries

import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

/** An example that executes a CTE declaration directly, without selecting from it in an outer query. */
interface IExampleV31 {
    fun execute(queryManager: IQueryBuilderExecutor)
}
