package gog.my_project.data_base.query.example.v2.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base.query.example.v2.queries.models.UserPhoneModelV2
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v2.queries.IExampleV2
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A1ExampleSelectV2 : IExampleV2<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .select {
            addColumn { column(UserModelV2::class, UserModelV2::id) }
            addColumn { column(UserModelV2::class, UserModelV2::name) }
            addColumn { column(UserModelV2::class, UserModelV2::family) }
            addColumn { column(UserModelV2::class, UserModelV2::age) }
            addColumn { column(UserPhoneModelV2::class, UserPhoneModelV2::phone) }
        }
        .table { table(UserModelV2::class) }
        .joins {
            addJoin {
                innerJoin()
                table { table(UserPhoneModelV2::class) }
                condition {
                    logicalOn()
                    addCondition {
                        sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                        operationEqual()
                        sideValue { tableColumn(UserPhoneModelV2::class, UserPhoneModelV2::userId, "up") }
                    }
                }
            }
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                    operationEqual()
                    sideValue("id1", 1)
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV2-Ex1: Model-backed select with phone join\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: user_id=${row.getValue(UserModelV2::id)}, " +
                            "user_name=${row.getValue(UserModelV2::name)}, " +
                            "user_family=${row.getValue(UserModelV2::family)}, " +
                            "user_age=${row.getValue(UserModelV2::age)}, " +
                            "user_phone=${row.getValue(UserPhoneModelV2::phone)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
