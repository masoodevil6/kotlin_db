package gog.my_project.data_base.query.example.v2.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.ast.enums.DataType

import gog.my_project.data_base.query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v2.queries.IExampleV2

import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A7ExampleSelectV2 : IExampleV2<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .select {
            addColumn { column(UserModelV2::class, UserModelV2::id) }
            addColumn { column { tableAttribute("concat( uu.name , '-' , uu.family )") }; alias("user_full_name"); execute(DataType.STRING) }
            addColumn { column(UserModelV2::class, UserModelV2::age) }
        }
        .table { table(UserModelV2::class) }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV2-Ex7: Query table attribute\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: user_id=${row.getValue(UserModelV2::id)}, " +
                            "user_full_name=${row["user_full_name"]}, " +
                            "user_age=${row.getValue(UserModelV2::age)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
