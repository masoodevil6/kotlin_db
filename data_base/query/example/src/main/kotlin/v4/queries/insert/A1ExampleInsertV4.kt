package gog.my_project.data_base.query.example.v4.queries.insert

import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.models.UserModelV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import java.util.UUID

/** V2-style INSERT example that reports the generated id and leaves the inserted row intact. */
class A1ExampleInsertV4(
    private val name: String = "v4_${UUID.randomUUID().toString().replace("-", "")}",
    private val family: String = "${name}_family",
    private val age: Int = 31,
) : IExampleV4 {
    fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table(UserModelV4::class) }
        .addValue {
            column(UserModelV4::class, UserModelV4::name)
            setValue {
                paramName("v4_user_name")
                paramValue(name)
            }
        }
        .addValue {
            column(UserModelV4::class, UserModelV4::family)
            setValue {
                paramName("v4_user_family")
                paramValue(family)
            }
        }
        .addValue {
            column(UserModelV4::class, UserModelV4::age)
            setValue {
                paramName("v4_user_age")
                paramValue(age)
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager.queryBuilder(query())
            .sql { builtQuery ->
                println("\n=============================================\nV4-Insert query: ${builtQuery.query}; params: ${builtQuery.params}")
            }
            .execute()
            .success { generatedKey ->
                println("V4-Insert generated key: $generatedKey")
                generatedKey
            }
            .error { failure -> println("V4-Insert error: $failure") }
            .toValue()
    }
}
