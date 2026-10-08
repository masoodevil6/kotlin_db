package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPhoneRelationV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A1ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .from(
            UserPhoneRelationV3().queryRelation(UserPhoneRelationV3.RelationFilters()),
            "details",
        )
        .select {
            addColumn { relationColumn(UserPhoneRelationV3::id, "details") }
            addColumn { relationColumn(UserPhoneRelationV3::name, "details") }
            addColumn { relationColumn(UserPhoneRelationV3::family, "details") }
            addColumn { relationColumn(UserPhoneRelationV3::age, "details") }
            addColumn { relationColumn(UserPhoneRelationV3::phone, "details") }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex1: Relation-backed select with phone join\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue(UserPhoneRelationV3::id)}, " +
                            "name=${row.getValue(UserPhoneRelationV3::name)}, " +
                            "family=${row.getValue(UserPhoneRelationV3::family)}, " +
                            "age=${row.getValue(UserPhoneRelationV3::age)}, " +
                            "phone=${row.getValue(UserPhoneRelationV3::phone)}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
