package gog.my_project.data_base.query.example.v3.queries.delete

import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleDeleteV3 : IExampleV3<IQueryRenderDeleteApi> {
    override fun query(): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        .addTarget("uu")
        .table { table(UserModelV3::class) }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn(UserModelV3::class, UserModelV3::id, "uu") }
                    operationEqual()
                    sideValue("id1", 5)
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder(query())
            .sql { builtQuery -> println("V3-Delete query: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { affectedRows ->
                println("V3-Delete: $affectedRows row(s) deleted")
                affectedRows
            }
            .error { failure -> println("V3-Delete error: $failure") }
    }
}
