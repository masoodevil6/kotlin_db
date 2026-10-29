package gog.my_project.data_base.query.example.v4.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.models.UserModelV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toList
import java.util.UUID

/** Returns the original ExecuteResult.Failure from a controlled SQL error without translating it. */
class A7ExampleSelectV4 : IExampleV4 {
    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager.transaction { tx ->
            tx.queryBuilder(missingColumnQuery())
                .sql { builtQuery -> println("V4 controlled SQL failure query: ${builtQuery.query}") }
                .execute()
                .success { rows -> rows }
                .error { failure -> println("V4 original SQL failure preserved: ${failure.message}") }
                .toList()
                .let { rows ->
                    ExecuteResult.Failure(
                        IllegalStateException(
                            "Controlled missing-column query unexpectedly succeeded with ${rows.size} row(s)",
                        ),
                    )
                }
        }
            .success { value -> println("V4 transaction unexpectedly succeeded: $value"); value }
            .error { failure -> println("V4 transaction returned Failure: ${failure.message}") }
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
