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

class A4ExampleSelectV1 : IExampleV1<IQueryRenderSelectApi> {
    override fun query(): IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .select {
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("id")
                }
                alias("user_id")
                execute(DataType.LONG)
            }
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("name")
                }
                alias("user_name")
                execute(DataType.STRING)
            }
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("family")
                }
                alias("user_family")
                execute(DataType.STRING)
            }
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("age")
                }
                alias("user_age")
                execute(DataType.INT)
            }
        }
        .from {
            table("user_users")
            alias("uu")
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector("uu")
                        columnSelector("age")
                    }
                    operationIs()
                    sideValueParam {
                        paramName("user_age")
                        paramValue(null)
                    }
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()
        val aliases = query.requireResultOutputAliases()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("\n=============================================\nV1-Ex4: Smart select with IS NULL\nquery: $queryText\nparams: $params")
            }
            .details { metadata ->
                println(formatEffectiveQueryMetadata(metadata))
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(aliases.joinToString(prefix = "row: ") { alias -> "$alias=${row.getValue(alias)}" })
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
