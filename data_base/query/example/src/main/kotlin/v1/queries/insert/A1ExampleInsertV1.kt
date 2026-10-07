package gog.my_project.data_base.query.example.v1.queries.insert

import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleInsertV1 : IExampleV1<IQueryRenderInsertApi> {
    override fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table("user_users").alias("uu") }
        .addValue { column("name", "Ali") }
        .addValue { column("family", "Sadegi") }
        .addValue { column("age", 50) }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val consumer = queryManager
            .queryBuilder(query())
            .sql { (query, params) ->
                println("\n=============================================\nV1-Ex1: Insert Sample\nquery: $query\nparams: $params")
            }
            .execute()
            .success { generatedKey ->
                println("generated key: $generatedKey")
                generatedKey
            }
            .error { failure -> println("error: $failure") }

    }
}
