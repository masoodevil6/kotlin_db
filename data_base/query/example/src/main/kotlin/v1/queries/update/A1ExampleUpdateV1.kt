package gog.my_project.data_base.query.example.v1.queries.update

import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.example.v1.queries.printMutationSqlV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleUpdateV1 : IExampleV1<IQueryRenderUpdateApi> {
    override fun query(): IQueryRenderUpdateApi = QueryRenderUpdateBuilder()
        .table {
            table("user_users").alias("uu")
        }
        .addValue { column("uu", "name", "--changed--") }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn("uu", "id") }
                    operationEqual()
                    sideValue("id1", 5)
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {

        val consumer = queryManager
            .queryBuilder(query())
            .sql { (query, params) ->
                println("\n=============================================\nV1-Ex1: Update Sample \nquery: $query\nparams: $params")
            }
            .execute()
            .success { affectedRows ->
                println("updated rows: $affectedRows")
                affectedRows
            }
            .error { failure -> println("error: $failure") }
    }
}
