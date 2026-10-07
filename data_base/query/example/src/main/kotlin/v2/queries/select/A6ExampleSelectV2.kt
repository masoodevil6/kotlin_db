package gog.my_project.data_base.query.example.v2.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.builder.ast.select_builder.requireResultOutputAliases
import gog.my_project.data_base.query.ast.enums.DataType

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v2.queries.IExampleV2

import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A6ExampleSelectV2 : IExampleV2<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .withs {
            addWith { with("cte_info_user", gog.my_project.data_base.query.builder.cte.modules.users.CteInfoUser(1)) }
        }
        .select {
            addColumn { column { cteColumn("ciu", "cte_user_id") }; alias("cte_user_id"); execute(DataType.LONG) }
            addColumn { column { cteColumn("ciu", "cte_user_name") }; alias("cte_user_name"); execute(DataType.STRING) }
            addColumn { column { cteColumn("ciu", "cte_user_family") }; alias("cte_user_family"); execute(DataType.STRING) }
            addColumn { column { cteColumn("ciu", "cte_user_age") }; alias("cte_user_age"); execute(DataType.INT) }
            addColumn { column { cteColumn("ciu", "cte_user_phone") }; alias("cte_user_phone"); execute(DataType.STRING) }
        }
        .table { cte("cte_info_user").alias("ciu") }
        .limit { setOptionLimit(2) }
        .offset { setOptionOffset(0) }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        val aliases = query.requireResultOutputAliases()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV2-Ex6: Query with CTE\nquery: $queryText\nparams: $params")
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
