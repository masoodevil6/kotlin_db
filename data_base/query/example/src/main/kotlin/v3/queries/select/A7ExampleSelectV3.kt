package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

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
            }
            addColumn {
                relationColumn(UserDisplayRelationV3::fullName)
            }
            addColumn {
                relationColumn(UserDisplayRelationV3::age)
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex7: Relation-backed select with table attribute\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue(UserDisplayRelationV3::id)}, " +
                            "fullName=${row.getValue(UserDisplayRelationV3::fullName)}, " +
                            "age=${row.getValue(UserDisplayRelationV3::age)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
