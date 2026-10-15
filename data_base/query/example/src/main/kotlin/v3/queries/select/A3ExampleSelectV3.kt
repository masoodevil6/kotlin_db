package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsQueryV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A3ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = configureQuery(QueryRenderSelectBuilder())

    private fun configureQuery(query: IQueryRenderSelectApi): IQueryRenderSelectApi = query
        .from {
            cte(UserStatsQueryV3(UserStatsQueryV3.RelationFilters()))
            alias("users")
        }
        .select {
            addColumn { cteOutput(UserStatsQueryV3::id, "users") }
            addColumn { cteOutput(UserStatsQueryV3::name, "users") }
            addColumn { cteOutput(UserStatsQueryV3::family, "users") }
            addColumn { cteOutput(UserStatsQueryV3::age, "users") }
        }
        .where {
            conditions {
                addGroup {
                    addCondition { logicalAnd(); sideSelector { tableColumn("users", "name") }; operationLike(); sideValue("user_name1", "Meh") }
                    addCondition { logicalOr(); sideSelector { tableColumn("users", "name") }; operationLike(); sideValue("user_name2", "Meh%") }
                    addCondition { logicalOr(); sideSelector { tableColumn("users", "name") }; operationLike(); sideValue("user_name3", "%Meh") }
                    addCondition { logicalOr(); sideSelector { tableColumn("users", "name") }; operationLike(); sideValue("user_name4", "%Meh%") }
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder { operations -> operations.querySelect { query -> configureQuery(query) } }
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex3: Relation-backed select with WhereLike\nquery: $queryText\nparams: $params")
            }
            .details { metadata ->
                println(formatEffectiveQueryMetadata(metadata))
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue(UserStatsQueryV3::id)}, " +
                            "name=${row.getValue(UserStatsQueryV3::name)}, " +
                            "family=${row.getValue(UserStatsQueryV3::family)}, " +
                            "age=${row.getValue(UserStatsQueryV3::age)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
