package gog.my_project.data_base.query.example.v1.queries.delete

import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleDeleteV1 : IExampleV1<IQueryRenderDeleteApi> {
    override fun query(): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        .table { table("user_users") }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn("", "id") }
                    operationEqual()
                    sideValue("id1", 5)
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val consumer = queryManager
            .queryBuilder(query())
            .sql { (query, params) ->
                println("\n=============================================\nV1-Ex1: Delete Sample\nquery: $query\nparams: $params")
            }
            .execute()
            .success { affectedRows ->
                println("deleted rows: $affectedRows")
                affectedRows
            }
            .error { failure -> println("error: $failure") }
    }
}
