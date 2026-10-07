package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.builder.ast.select_builder.requireResultOutputAliases
import gog.my_project.data_base.query.ast.enums.DataType

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsRelationV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A2ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .from(UserStatsRelationV3().queryRelation(UserStatsRelationV3.RelationFilters()))
        .select {
            addColumn { relationColumn(UserStatsRelationV3::id); alias(UserStatsRelationV3::id); execute(DataType.LONG) }
            addColumn { relationColumn(UserStatsRelationV3::name); alias(UserStatsRelationV3::name); execute(DataType.STRING) }
            addColumn { relationColumn(UserStatsRelationV3::family); alias(UserStatsRelationV3::family); execute(DataType.STRING) }
            addColumn { relationColumn(UserStatsRelationV3::age); alias(UserStatsRelationV3::age); execute(DataType.INT) }
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn("v3_user_stats", "id") }
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
                println("\n=============================================\nV3-Ex2: Relation-backed select with whereIn\nquery: $queryText\nparams: $params")
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
