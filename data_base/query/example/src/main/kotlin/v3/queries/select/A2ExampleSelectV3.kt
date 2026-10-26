package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.cte.mapper.CteQueryRowMapper
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsQueryV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A2ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = configureQuery(QueryRenderSelectBuilder())

    private fun configureQuery(query: IQueryRenderSelectApi): IQueryRenderSelectApi {
        val declaration = UserStatsQueryV3()
        val relationParams = UserStatsQueryV3.RelationFilters()
        val statsCte = declaration.createCte(relationParams)
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
                addColumn {
                    column {
                        tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::id)
                    }
                    alias("id")
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::name)
                    }
                    alias("name")
                    execute(DataType.STRING)
                }
                addColumn {
                    column {
                        tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::family)
                    }
                    alias("family")
                    execute(DataType.STRING)
                }
                addColumn {
                    column {
                        tableSelector(UserStatsQueryV3::class); columnSelector(UserStatsQueryV3::age)
                    }
                    alias("age")
                    execute(DataType.INT)
                }
            }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserStatsQueryV3::class)
                            columnSelector(UserStatsQueryV3::id)
                        }
                        operationIn()
                        sideValueCollection {
                            addParam {
                                paramName("userId1")
                                paramValue(1)
                            }
                            addParam {
                                paramName("userId2")
                                paramValue(2)
                            }
                        }
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
