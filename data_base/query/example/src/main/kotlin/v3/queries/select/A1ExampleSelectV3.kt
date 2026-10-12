package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPhoneQueryV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A1ExampleSelectV3 : IExampleV3<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = configureQuery(QueryRenderSelectBuilder())

    private fun configureQuery(query: IQueryRenderSelectApi): IQueryRenderSelectApi {
        val declaration = UserPhoneQueryV3(UserPhoneQueryV3.RelationFilters())
        val phoneCte = declaration.createCte(declaration.params)

        return query
            .withs {
                addWith { with(phoneCte) }
            }
            .from {
                cte(phoneCte.name)
                alias("details")
            }
            .select {
                addColumn {
                    column {
                        cteColumn("details", "user_id")
                    }
                    alias("id"); execute(DataType.INT)
                }
                addColumn { column { cteColumn("details", "user_name") }; alias("name"); execute(DataType.STRING) }
                addColumn { column { cteColumn("details", "user_family") }; alias("family"); execute(DataType.STRING) }
                addColumn { column { cteColumn("details", "user_age") }; alias("age"); execute(DataType.INT) }
                addColumn { column { cteColumn("details", "user_phone") }; alias("phone"); execute(DataType.STRING) }
            }
    }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder { operations -> operations.querySelect { query -> configureQuery(query) } }
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex1: WITH CTE select with phone join\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "row: id=${row.getValue("id")}, " +
                            "name=${row.getValue("name")}, " +
                            "family=${row.getValue("family")}, " +
                            "age=${row.getValue("age")}, " +
                            "phone=${row.getValue("phone")}",
                    )
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
