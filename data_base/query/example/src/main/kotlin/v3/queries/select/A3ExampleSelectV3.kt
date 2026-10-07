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

class A3ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .from(UserStatsRelationV3().queryRelation(UserStatsRelationV3.RelationFilters()), "users")
        .select {
            addColumn { relationColumn(UserStatsRelationV3::id, "users"); alias(UserStatsRelationV3::id); execute(DataType.LONG) }
            addColumn { relationColumn(UserStatsRelationV3::name, "users"); alias(UserStatsRelationV3::name); execute(DataType.STRING) }
            addColumn { relationColumn(UserStatsRelationV3::family, "users"); alias(UserStatsRelationV3::family); execute(DataType.STRING) }
            addColumn { relationColumn(UserStatsRelationV3::age, "users"); alias(UserStatsRelationV3::age); execute(DataType.INT) }
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
        val query = query()
        val aliases = query.requireResultOutputAliases()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex3: Relation-backed select with WhereLike\nquery: $queryText\nparams: $params")
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
