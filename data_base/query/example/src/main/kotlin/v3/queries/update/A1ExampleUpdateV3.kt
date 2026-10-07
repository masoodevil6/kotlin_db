package gog.my_project.data_base.query.example.v3.queries.update

import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleUpdateV3 : IExampleV3<IQueryRenderUpdateApi> {
    override fun query(): IQueryRenderUpdateApi = QueryRenderUpdateBuilder()
        .table { table(UserModelV3::class) }
        .addValue { column(UserModelV3::class, UserModelV3::name, "--changed--") }
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
            .sql { builtQuery -> println("V3-Update query: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { affectedRows ->
                println("V3-Update: $affectedRows row(s) updated")
                affectedRows
            }
            .error { failure -> println("V3-Update error: $failure") }
    }
}
