package gog.my_project.data_base.query.example.v1.queries.select

import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata
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
            distinct()
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("id")
                };
                alias("user_id");
                execute(DataType.LONG)
            }
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("name")
                };
                alias("user_name");
                execute(DataType.STRING)
            }
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("family")
                };
                alias("user_family");
                execute(DataType.STRING)
            }
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("age")
                };
                alias("user_age");
                execute(DataType.INT)
            }
            addColumn {
                column {
                    tableSelector("up")
                    columnSelector("phone")
                };
                alias("user_phone");
                execute(DataType.STRING)
            }
        }
        .from {
            table("user_users")
            alias("uu")
        }
        .joins {
            addJoin {
                innerJoin()
                table { table("user_phones").alias("up") }
                condition {
                    logicalOn()
                    addCondition {
                        sideSelector {
                            tableSelector("uu")
                            columnSelector("id")
                        }
                        operationEqual()
                        sideValueColumn {
                            tableSelector("up")
                            columnSelector("user_id")
                        }
                    }
                }
            }
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector("uu"); columnSelector("id")
                    }
                    operationEqual()
                    sideValueParam{
                        paramName("id1")
                        paramValue(1)
                    }
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
            .details { metadata ->
                println(formatEffectiveQueryMetadata(metadata))
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
