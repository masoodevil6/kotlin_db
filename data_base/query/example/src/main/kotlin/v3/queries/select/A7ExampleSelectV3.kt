package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.builder.ast.select_builder.requireResultOutputAliases
import gog.my_project.data_base.query.ast.enums.DataType

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserDisplayRelationV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A7ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .from(UserDisplayRelationV3().queryRelation(UserDisplayRelationV3.RelationFilters()))
        .select {
            addColumn {
                relationColumn(UserDisplayRelationV3::id)
                alias(UserDisplayRelationV3::id); execute(DataType.LONG)
            }
            addColumn {
                relationColumn(UserDisplayRelationV3::fullName)
                alias(UserDisplayRelationV3::fullName); execute(DataType.STRING)
            }
            addColumn {
                relationColumn(UserDisplayRelationV3::age)
                alias(UserDisplayRelationV3::age); execute(DataType.INT)
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        val aliases = query.requireResultOutputAliases()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex7: Relation-backed select with table attribute\nquery: $queryText\nparams: $params")
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
