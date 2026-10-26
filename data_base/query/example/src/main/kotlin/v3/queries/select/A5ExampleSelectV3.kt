package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.cte.mapper.CteQueryRowMapper
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsQueryV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A5ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = configureQuery(QueryRenderSelectBuilder())

    private fun configureQuery(query: IQueryRenderSelectApi): IQueryRenderSelectApi = query
        .from { cte(UserStatsQueryV3()(UserStatsQueryV3.RelationFilters())) }
        .select {
            addColumn { column { tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::id) } }
            addColumn { column { tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::name) } }
            addColumn { column { tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::family) } }
            addColumn { column { tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::age) } }
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector(UserStatsQueryV3::class)
                        columnSelector(UserStatsQueryV3::age)
                    }
                    operationGreaterThan()
                    sideValueParam {
                        paramName("user_age")
                        paramValue(1)
                    }
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder { operations -> operations.querySelect { query -> configureQuery(query) } }
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex5: Relation-backed version of the original IS condition\nquery: $queryText\nparams: $params")
            }
            .details { metadata ->
                println(formatEffectiveQueryMetadata(metadata))
            }
            .executeAs { row -> CteQueryRowMapper.map(UserStatsQueryV3::class, row) }
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.id}, " +
                            "name=${row.name}, " +
                            "family=${row.family}, " +
                            "age=${row.age}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
