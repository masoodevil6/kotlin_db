package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsRelationV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A4ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .from(UserStatsRelationV3().queryRelation(UserStatsRelationV3.RelationFilters()))
        .select {
            addColumn { relationColumn(UserStatsRelationV3::id) }
            addColumn { relationColumn(UserStatsRelationV3::name) }
            addColumn { relationColumn(UserStatsRelationV3::family) }
            addColumn { relationColumn(UserStatsRelationV3::age) }
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn("v3_user_stats", "age") }
                    operationIs()
                    sideValue("user_age", null)
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex4: Relation-backed select with WhereIsNull\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue(UserStatsRelationV3::id)}, " +
                            "name=${row.getValue(UserStatsRelationV3::name)}, " +
                            "family=${row.getValue(UserStatsRelationV3::family)}, " +
                            "age=${row.getValue(UserStatsRelationV3::age)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
