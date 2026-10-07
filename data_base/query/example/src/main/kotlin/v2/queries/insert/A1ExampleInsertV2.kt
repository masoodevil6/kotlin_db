package gog.my_project.data_base.query.example.v2.queries.insert

import gog.my_project.data_base.query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.example.v2.queries.IExampleV2
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleInsertV2 : IExampleV2<IQueryRenderInsertApi> {
    override fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table(UserModelV2::class) }
        .addValue { column(UserModelV2::class, UserModelV2::name, "Ali") }
        .addValue { column(UserModelV2::class, UserModelV2::family, "Sadegi") }
        .addValue { column(UserModelV2::class, UserModelV2::age, 50) }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder(query())
            .sql { builtQuery -> println("V2-Insert query: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { generatedKey ->
                println("V2-Insert: $generatedKey")
                generatedKey
            }
            .error { failure -> println("V2-Insert error: $failure") }
    }
}
