package gog.my_project.data_base.query.example.v4.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.delete.A1ExampleDeleteV4
import gog.my_project.data_base.query.example.v4.queries.insert.A1ExampleInsertV4
import gog.my_project.data_base.query.example.v4.queries.models.UserModelV4
import gog.my_project.data_base.query.example.v4.queries.relations.UserDisplayQueryV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toCollection
import java.util.UUID

/** Demonstrates that an ignored scoped Failure still prevents transaction commit. */
class A5ExampleSelectV4 : IExampleV4 {
    override fun execute(queryManager: IQueryBuilderExecutor) {
        val marker = "v4_ig_${UUID.randomUUID().toString().replace("-", "")}"
        var userId: Int? = null
        try {
            queryManager.transaction { tx ->
                userId = Math.toIntExact(
                    tx.queryBuilder(A1ExampleInsertV4(marker, "${marker}_family", 36).query())
                        .sql { builtQuery -> println("V4 ignored-failure fixture INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                        .execute()
                        .success { key -> key }
                        .error { failure -> println("V4 ignored-failure fixture INSERT failed: ${failure.message}") }
                        .toValue(),
                )
                tx.queryBuilder(missingColumnQuery())
                    .sql { builtQuery -> println("V4 controlled failure SELECT: ${builtQuery.query}") }
                    .execute()
                    .success { rows ->
                        throw IllegalStateException(
                            "Controlled missing-column SELECT unexpectedly succeeded with ${rows.size} row(s)",
                        )
                    }
                    .error { failure ->
                        println("V4 deliberately ignored query Failure: ${failure.message}")
                    }
                ExecuteResult.Success("callback returned success after ignoring the failed query")
            }
                .success { value -> println("V4 ignored Failure scenario unexpectedly committed: $value"); value }
                .error { failure -> println("V4 ignored Failure transaction: ${failure.message}") }
            userId?.let { verifyAbsent(queryManager, it) }
        } finally {
            cleanupUser(queryManager, userId)
        }
    }
}

private fun missingColumnQuery(): IQueryRenderSelectApi {
    val column = "missing_v4_${UUID.randomUUID().toString().replace("-", "")}"
    return QueryRenderSelectBuilder()
        .from { model(UserModelV4::class) }
        .select {
            addColumn {
                column { tableSelector(UserModelV4::class); columnSelector(column) }
                alias("missing_probe")
                execute(DataType.INT)
            }
        }
}

private fun verifyAbsent(queryManager: IQueryBuilderExecutor, userId: Int) {
    UserDisplayQueryV4().execute(queryManager, UserDisplayQueryV4.RelationFilters(userId = userId))
        .success { users ->
            println("V4 ignored Failure user absent=${users.size == 0}")
            users
        }
        .error { failure -> println("V4 ignored Failure visibility check failed: ${failure.message}") }
        .toCollection()
}

private fun cleanupUser(queryManager: IQueryBuilderExecutor, userId: Int?) {
    userId?.let { id ->
        queryManager.transaction { tx ->
            tx.queryBuilder(A1ExampleDeleteV4().userQuery(id)).execute()
        }
            .success { println("V4 ignored-failure cleanup affected $it row(s)"); it }
            .error { failure -> println("V4 ignored-failure cleanup failed: ${failure.message}") }
    }
}
