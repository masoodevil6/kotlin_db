package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPageRelationV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A6ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .from(UserPageRelationV3().queryRelation(UserPageRelationV3.RelationFilters()))
        .select {
            addColumn { relationColumn(UserPageRelationV3::id) }
            addColumn { relationColumn(UserPageRelationV3::name) }
            addColumn { relationColumn(UserPageRelationV3::family) }
            addColumn { relationColumn(UserPageRelationV3::age) }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex6: Nested QueryRelation with limit and offset\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue(UserPageRelationV3::id)}, " +
                            "name=${row.getValue(UserPageRelationV3::name)}, " +
                            "family=${row.getValue(UserPageRelationV3::family)}, " +
                            "age=${row.getValue(UserPageRelationV3::age)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
