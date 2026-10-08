package gog.my_project.data_base.query.example.v1.queries.select

import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.builder.ast.select_builder.requireResultOutputAliases
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.get

class A1ExampleSelectV1 : IExampleV1<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .select {
            addColumn {
                column { tableColumn("uu", "id") };
                alias("user_id");
                execute(DataType.LONG)
            }
            addColumn {
                column { tableColumn("uu", "name") };
                alias("user_name");
                execute(DataType.STRING)
            }
            addColumn {
                column { tableColumn("uu", "family") };
                alias("user_family");
                execute(DataType.STRING)
            }
            addColumn {
                column { tableColumn("uu", "age") };
                alias("user_age");
                execute(DataType.INT)
            }
            addColumn {
                column { tableColumn("up", "phone") };
                alias("user_phone");
                execute(DataType.STRING)
            }
        }
        .table { table("user_users").alias("uu") }
        .joins {
            addJoin {
                innerJoin()
                table { table("user_phones").alias("up") }
                condition {
                    logicalOn()
                    addCondition {
                        sideSelector { tableColumn("uu", "id") }
                        operationEqual()
                        sideValue { tableColumn("up", "user_id") }
                    }
                }
            }
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn("uu", "id") }
                    operationEqual()
                    sideValue("id1", 1)
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        val aliases = query.requireResultOutputAliases()

        val result  = queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV1-Ex1: Smart select with join\nquery: $queryText\nparams: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    row["full_name"] =  "${row.getValue("user_name") ?: ""}-${row.getValue("user_family") ?: ""}"
                    println(aliases.joinToString(prefix = "row: ") { alias -> "$alias=${row.getValue(alias)}" })
                }
                rows
            }
            .error { failure -> println("error: $failure") }

        //println("my test ====> ${result}")
        println("my test ====> ${result[0].getValue("full_name")}")
    }
}
