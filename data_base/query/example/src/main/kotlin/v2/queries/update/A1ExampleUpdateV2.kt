package gog.my_project.data_base.query.example.v2.queries.update

import gog.my_project.data_base.query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.example.v2.queries.IExampleV2
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleUpdateV2 : IExampleV2<IQueryRenderUpdateApi> {
    override fun query(): IQueryRenderUpdateApi = QueryRenderUpdateBuilder()
        .table { table(UserModelV2::class) }
        .addValue { column(UserModelV2::class, UserModelV2::name, "--changed--") }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                    operationEqual()
                    sideValue("id1", 5)
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder(query())
            .sql { builtQuery -> println("V2-Update query: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { affectedRows ->
                println("V2-Update: $affectedRows row(s) updated")
                affectedRows
            }
            .error { failure -> println("V2-Update error: $failure") }
    }
}
