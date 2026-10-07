package gog.my_project.data_base.query.example.v3.queries.insert

import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.example.v3.queries.IExampleV3
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleInsertV3 : IExampleV3<IQueryRenderInsertApi> {
    override fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table(UserModelV3::class) }
        .addValue { column(UserModelV3::class, UserModelV3::name, "Ali") }
        .addValue { column(UserModelV3::class, UserModelV3::family, "Sadegi") }
        .addValue { column(UserModelV3::class, UserModelV3::age, 50) }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager
            .queryBuilder(query())
            .sql { builtQuery -> println("V3-Insert query: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { generatedKey ->
                println("V3-Insert: $generatedKey")
                generatedKey
            }
            .error { failure -> println("V3-Insert error: $failure") }
    }
}
