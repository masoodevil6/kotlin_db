package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsQueryV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A2ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = configureQuery(QueryRenderSelectBuilder())

    private fun configureQuery(query: IQueryRenderSelectApi): IQueryRenderSelectApi {
        val declaration = UserStatsQueryV3(UserStatsQueryV3.RelationFilters())
        val statsCte = declaration.createCte(declaration.params)
        val withName = "active_user_stats_v3"

        return query
            .withs {
                addWith { with(withName, statsCte) }
            }
            .from {
                cte(withName)
                alias("users")
            }
            .select {
                addColumn { column { cteColumn("users", "id") }; alias("id"); execute(DataType.INT) }
                addColumn { column { cteColumn("users", "name") }; alias("name"); execute(DataType.STRING) }
                addColumn { column { cteColumn("users", "family") }; alias("family"); execute(DataType.STRING) }
                addColumn { column { cteColumn("users", "age") }; alias("age"); execute(DataType.INT) }
            }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("users", "id") }
                        operationIn()
                        sideValueCollection("userId") { addParam(1); addParam(2) }
                    }
                }
            }
    }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder { operations -> operations.querySelect { query -> configureQuery(query) } }
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex2: Explicitly named WITH CTE select with whereIn\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue("id")}, " +
                            "name=${row.getValue("name")}, " +
                            "family=${row.getValue("family")}, " +
                            "age=${row.getValue("age")}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
