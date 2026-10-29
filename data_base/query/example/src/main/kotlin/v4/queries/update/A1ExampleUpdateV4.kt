package gog.my_project.data_base.query.example.v4.queries.update

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.delete.A1ExampleDeleteV4
import gog.my_project.data_base.query.example.v4.queries.insert.A1ExampleInsertV4
import gog.my_project.data_base.query.example.v4.queries.models.UserModelV4
import gog.my_project.data_base.query.example.v4.queries.relations.UserDisplayQueryV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toCollection
import java.util.UUID

/** V2-style UPDATE query builder, exercised with an isolated generated user inside one transaction. */
class A1ExampleUpdateV4 : IExampleV4 {
    fun query(userId: Int, expectedName: String, updatedName: String): IQueryRenderUpdateApi =
        QueryRenderUpdateBuilder()
            .table { table(UserModelV4::class) }
            .addValue {
                column(UserModelV4::class, UserModelV4::name)
                setValue { paramName("v4_updated_name"); paramValue(updatedName) }
            }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableSelector(UserModelV4::class); columnSelector(UserModelV4::id) }
                        operationEqual()
                        sideValueParam { paramName("v4_update_id"); paramValue(userId) }
                    }
                    addCondition {
                        logicalAnd()
                        sideSelector { tableSelector(UserModelV4::class); columnSelector(UserModelV4::name) }
                        operationEqual()
                        sideValueParam { paramName("v4_update_expected_name"); paramValue(expectedName) }
                    }
                }
            }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val marker = "v4_up_${UUID.randomUUID().toString().replace("-", "")}" 
        val insertedUser = A1ExampleInsertV4(marker, "${marker}_family", 32)
        queryManager.transaction { tx ->
            val userId = Math.toIntExact(
                tx.queryBuilder(insertedUser.query())
                    .sql { builtQuery -> println("V4 UPDATE fixture INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                    .execute()
                    .success { key -> key }
                    .error { failure -> println("V4 UPDATE fixture INSERT failed: ${failure.message}") }
                    .toValue(),
            )
            val updatedName = "${marker}_updated"

            tx.queryBuilder(query(userId, marker, updatedName))
                .sql { builtQuery -> println("V4 UPDATE: ${builtQuery.query}; params=${builtQuery.params}") }
                .execute()
                .success { affectedRows ->
                    check(affectedRows == 1) { "V4 UPDATE expected one affected generated user row" }
                    affectedRows
                }
                .error { failure -> println("V4 UPDATE failed: ${failure.message}") }
                .toValue()

            UserDisplayQueryV4().execute(
                tx,
                UserDisplayQueryV4.RelationFilters(userId = userId),
            )
                .success { rows ->
                    check(rows.firstOrNull { row -> row.fullName == "${updatedName}-${marker}_family" } != null) {
                        "V4 UPDATE typed readback did not show the updated name"
                    }
                    rows
                }
                .error { failure -> println("V4 UPDATE typed readback failed: ${failure.message}") }
                .toCollection()

            tx.queryBuilder(A1ExampleDeleteV4().userQuery(userId))
                .sql { builtQuery -> println("V4 UPDATE fixture cleanup: ${builtQuery.query}; params=${builtQuery.params}") }
                .execute()
                .success { deletedRows ->
                    check(deletedRows == 1) { "V4 UPDATE fixture cleanup did not delete one row" }
                    deletedRows
                }
                .error { failure -> println("V4 UPDATE fixture cleanup failed: ${failure.message}") }
                .toValue()

            ExecuteResult.Success("updated user $userId and verified the typed CTE readback")
        }
            .success { message -> println("V4 UPDATE transaction succeeded: $message"); message }
            .error { failure -> println("V4 UPDATE transaction failed: ${failure.message}") }
            .toValue()
    }
}
