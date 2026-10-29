package gog.my_project.data_base.query.example.v4.queries.insert

import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.models.UserPhoneModelV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import java.util.UUID

/** INSERT builder for a child phone row, used by examples after they create their own parent user. */
class A2ExampleInsertPhoneV4(
    private val userId: Int,
    private val phone: String = "v4_phone_${UUID.randomUUID().toString().replace("-", "")}",
) : IExampleV4 {
    fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table(UserPhoneModelV4::class) }
        .addValue {
            column(UserPhoneModelV4::class, UserPhoneModelV4::userId)
            setValue { paramName("v4_phone_user_id"); paramValue(userId) }
        }
        .addValue {
            column(UserPhoneModelV4::class, UserPhoneModelV4::phone)
            setValue { paramName("v4_phone_value"); paramValue(phone) }
        }

    /** Requires an existing parent id and leaves the inserted phone row intact. */
    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager.queryBuilder(query())
            .sql { builtQuery -> println("V4 phone INSERT: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { generatedKey ->
                println("V4 phone INSERT generated key: $generatedKey")
                generatedKey
            }
            .error { failure -> println("V4 phone INSERT error: $failure") }
            .toValue()
    }
}
