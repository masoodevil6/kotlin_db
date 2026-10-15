package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPageQueryV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A6ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = configureQuery(QueryRenderSelectBuilder())

    private fun configureQuery(query: IQueryRenderSelectApi): IQueryRenderSelectApi = query
        .from { cte(UserPageQueryV3(UserPageQueryV3.RelationFilters())) }
        .select {
            addColumn { cteOutput(UserPageQueryV3::id) }
            addColumn { cteOutput(UserPageQueryV3::name) }
            addColumn { cteOutput(UserPageQueryV3::family) }
            addColumn { cteOutput(UserPageQueryV3::age) }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder { operations -> operations.querySelect { query -> configureQuery(query) } }
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex6: Nested Cte with limit and offset\nquery: $queryText\nparams: $params")
            }
            .details { metadata ->
                println(formatEffectiveQueryMetadata(metadata))
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue(UserPageQueryV3::id)}, " +
                            "name=${row.getValue(UserPageQueryV3::name)}, " +
                            "family=${row.getValue(UserPageQueryV3::family)}, " +
                            "age=${row.getValue(UserPageQueryV3::age)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
