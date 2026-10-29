package gog.my_project.data_base.query.example.v4.queries.delete

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.insert.A1ExampleInsertV4
import gog.my_project.data_base.query.example.v4.queries.insert.A2ExampleInsertPhoneV4
import gog.my_project.data_base.query.example.v4.queries.models.UserModelV4
import gog.my_project.data_base.query.example.v4.queries.models.UserPhoneModelV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import java.util.UUID

/** V2-style DELETE query builder and transaction example, scoped to its own generated user/phone ids. */
class A1ExampleDeleteV4 : IExampleV4 {
    fun phoneQuery(userId: Int, phoneId: Int): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        .table { table(UserPhoneModelV4::class) }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableSelector(UserPhoneModelV4::class); columnSelector(UserPhoneModelV4::userId) }
                    operationEqual()
                    sideValueParam { paramName("v4_delete_phone_user_id"); paramValue(userId) }
                }
                addCondition {
                    logicalAnd()
                    sideSelector { tableSelector(UserPhoneModelV4::class); columnSelector(UserPhoneModelV4::id) }
                    operationEqual()
                    sideValueParam { paramName("v4_delete_phone_id"); paramValue(phoneId) }
                }
            }
        }

    fun userQuery(userId: Int): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        .table { table(UserModelV4::class) }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector(UserModelV4::class)
                        columnSelector(UserModelV4::id)
                    }
                    operationEqual()
                    sideValueParam { paramName("v4_delete_user_id"); paramValue(userId) }
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        /*queryManager.transaction { tx ->

            val effectedRow = tx.queryBuilder{
                operation -> operation.queryDelete {
                    QueryRenderDeleteBuilder()
                    .table { table(UserPhoneModelV4::class) }
                    .where {
                        conditions {
                            addCondition {
                                logicalAnd()
                                sideSelector { tableSelector(UserPhoneModelV4::class); columnSelector(UserPhoneModelV4::userId) }
                                operationEqual()
                                sideValueParam { paramName("v4_delete_phone_user_id"); paramValue(userId) }
                            }
                            addCondition {
                                logicalAnd()
                                sideSelector { tableSelector(UserPhoneModelV4::class); columnSelector(UserPhoneModelV4::id) }
                                operationEqual()
                                sideValueParam { paramName("v4_delete_phone_id"); paramValue(phoneId) }
                            }
                        }
                    }
                }
            }.sql{ builtQuery -> println("V4 DELETE fixture user INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                .execute()
                .toValue();

            if (effectedRow != null || effectedRow == 0) {
                val error =IllegalStateException("don't delete row user");
                ExecuteResult.Failure(error);
                throw error;
            }




            ExecuteResult.Success(true);
        }*/




        val marker = "v4_del_${UUID.randomUUID().toString().replace("-", "")}" 
        queryManager.transaction { tx ->
            val userId = Math.toIntExact(
                tx.queryBuilder(A1ExampleInsertV4(marker, "${marker}_family", 33).query())
                    .sql { builtQuery -> println("V4 DELETE fixture user INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                    .execute()
                    .success { userKey -> userKey }
                    .error { failure -> println("V4 DELETE fixture user INSERT failed: ${failure.message}") }
                    .toValue(),
            )
            val phoneId = Math.toIntExact(
                tx.queryBuilder(A2ExampleInsertPhoneV4(userId, "${marker}_phone").query())
                    .sql { builtQuery -> println("V4 DELETE fixture phone INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                    .execute()
                    .success { phoneKey -> phoneKey }
                    .error { failure -> println("V4 DELETE fixture phone INSERT failed: ${failure.message}") }
                    .toValue(),
            )

            tx.queryBuilder(phoneQuery(userId, phoneId))
                .sql { builtQuery -> println("V4 DELETE phone: ${builtQuery.query}; params=${builtQuery.params}") }
                .execute()
                .success { deletedRows ->
                    check(deletedRows == 1) { "V4 DELETE expected one generated phone row" }
                    deletedRows
                }
                .error { failure -> println("V4 DELETE phone failed: ${failure.message}") }
                .toValue()

            tx.queryBuilder(userQuery(userId))
                .sql { builtQuery -> println("V4 DELETE user: ${builtQuery.query}; params=${builtQuery.params}") }
                .execute()
                .success { deletedRows ->
                    check(deletedRows == 1) { "V4 DELETE expected one generated user row" }
                    deletedRows
                }
                .error { failure -> println("V4 DELETE user failed: ${failure.message}") }
                .toValue()

            ExecuteResult.Success("deleted generated phone $phoneId and user $userId")
        }
            .success { message -> println("V4 DELETE transaction succeeded: $message"); message }
            .error { failure -> println("V4 DELETE transaction failed: ${failure.message}") }
            .toValue()
    }
}
