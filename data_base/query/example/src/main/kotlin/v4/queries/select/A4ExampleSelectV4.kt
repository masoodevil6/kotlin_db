package gog.my_project.data_base.query.example.v4.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.delete.A1ExampleDeleteV4
import gog.my_project.data_base.query.example.v4.queries.insert.A1ExampleInsertV4
import gog.my_project.data_base.query.example.v4.queries.relations.UserDisplayQueryV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toCollection
import gog.my_project.data_base.query.executer.result.toList
import java.util.UUID

/** A failed second SQL operation rolls back the generated user inserted first. */
class A4ExampleSelectV4 : IExampleV4 {
    override fun execute(queryManager: IQueryBuilderExecutor) {
        val marker = "v4_rb_${UUID.randomUUID().toString().replace("-", "")}"
        var userId: Int? = null
        try {
            queryManager.transaction { tx ->
                userId = Math.toIntExact(
                    tx.queryBuilder(A1ExampleInsertV4(marker, "${marker}_family", 35).query())
                        .sql { builtQuery -> println("V4 rollback fixture INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                        .execute()
                        .success { key -> key }
                        .error { failure -> println("V4 rollback fixture INSERT failed: ${failure.message}") }
                        .toValue(),
                )
                tx.queryBuilder(missingColumnQuery())
                    .sql { builtQuery -> println("V4 controlled failure SELECT: ${builtQuery.query}") }
                    .execute()
                    .success { rows -> rows }
                    .error { failure -> println("V4 controlled SQL failure: ${failure.message}") }
                    .toList()
                    .let { rows ->
                        ExecuteResult.Failure(
                            IllegalStateException(
                                "Controlled missing-column SELECT unexpectedly succeeded with ${rows.size} row(s)",
                            ),
                        )
                    }
            }
                .success { value -> println("V4 rollback scenario unexpectedly committed: $value"); value }
                .error { failure -> println("V4 rollback after second query failure: ${failure.message}") }
            userId?.let { verifyAbsent(queryManager, it) }
        } finally {
            cleanupUser(queryManager, userId)
        }
    }
}

private fun missingColumnQuery(): IQueryRenderSelectApi {
    val column = "missing_v4_${UUID.randomUUID().toString().replace("-", "")}"
    return QueryRenderSelectBuilder()
        .from { model(gog.my_project.data_base.query.example.v4.queries.models.UserModelV4::class) }
        .select {
            addColumn {
                column {
                    tableSelector(gog.my_project.data_base.query.example.v4.queries.models.UserModelV4::class)
                    columnSelector(column)
                }
                alias("missing_probe")
                execute(DataType.INT)
            }
        }
}

private fun verifyAbsent(queryManager: IQueryBuilderExecutor, userId: Int) {
    UserDisplayQueryV4().execute(queryManager, UserDisplayQueryV4.RelationFilters(userId = userId))
        .success { users ->
            println("V4 rollback generated user absent=${users.size == 0}")
            users
        }
        .error { failure -> println("V4 rollback visibility check failed: ${failure.message}") }
        .toCollection()
}

private fun cleanupUser(queryManager: IQueryBuilderExecutor, userId: Int?) {
    userId?.let { id ->
        queryManager.transaction { tx ->
            tx.queryBuilder(A1ExampleDeleteV4().userQuery(id))
                .execute()
                .success { deletedRows -> deletedRows }
        }
            .success { println("V4 rollback fixture cleanup affected $it row(s)"); it }
            .error { failure -> println("V4 rollback fixture cleanup failed: ${failure.message}") }
    }
}
