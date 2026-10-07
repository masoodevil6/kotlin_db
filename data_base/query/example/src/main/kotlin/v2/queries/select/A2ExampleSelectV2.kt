package gog.my_project.data_base.query.example.v2.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.builder.ast.select_builder.requireResultOutputAliases
import gog.my_project.data_base.query.ast.enums.DataType

import gog.my_project.data_base.query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v2.queries.IExampleV2

import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A2ExampleSelectV2 : IExampleV2<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .select {
            addColumn { column(UserModelV2::class, UserModelV2::id); execute(DataType.LONG) }
            addColumn { column(UserModelV2::class, UserModelV2::name); execute(DataType.STRING) }
            addColumn { column(UserModelV2::class, UserModelV2::family); execute(DataType.STRING) }
            addColumn { column(UserModelV2::class, UserModelV2::age); execute(DataType.INT) }
        }
        .table { table(UserModelV2::class) }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                    operationIn()
                    sideValueCollection("userId") { addParam(1); addParam(2) }
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        val aliases = query.requireResultOutputAliases()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV2-Ex2: Model-backed select with whereIn\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(aliases.joinToString(prefix = "row: ") { alias -> "$alias=${row[alias]}" })
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
