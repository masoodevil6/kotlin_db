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

class A3ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = configureQuery(QueryRenderSelectBuilder())

    private fun configureQuery(query: IQueryRenderSelectApi): IQueryRenderSelectApi = query
        .from {
            cte(UserStatsQueryV3()(UserStatsQueryV3.RelationFilters()))
        }
        .select {
            addColumn {
                column { tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::id) }
            }
            addColumn {
                column { tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::name) }
            }
            addColumn {
                column { tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::family) }
            }
            addColumn {
                column { tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::age) }
            }
        }
        .where {
            conditions {
                addGroup {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserStatsQueryV3::class)
                            columnSelector(UserStatsQueryV3::name)
                        }
                        operationLike()
                        sideValueParam {
                            paramName("user_name1")
                            paramValue("Meh")
                        }
                    }
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserStatsQueryV3::class)
                            columnSelector(UserStatsQueryV3::name)
                        }
                        operationLike()
                        sideValueParam {
                            paramName("user_name2")
                            paramValue("Meh%")
                        }
                    }
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserStatsQueryV3::class)
                            columnSelector(UserStatsQueryV3::name)
                        }
                        operationLike()
                        sideValueParam {
                            paramName("user_name3")
                            paramValue("%Meh")
                        }
                    }
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserStatsQueryV3::class)
                            columnSelector(UserStatsQueryV3::name)
                        }
                        operationLike()
                        sideValueParam {
                            paramName("user_name4")
                            paramValue("%Meh%")
                        }
                    }
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
