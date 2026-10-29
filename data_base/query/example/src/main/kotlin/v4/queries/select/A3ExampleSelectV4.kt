package gog.my_project.data_base.query.example.v4.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.delete.A1ExampleDeleteV4
import gog.my_project.data_base.query.example.v4.queries.insert.A1ExampleInsertV4
import gog.my_project.data_base.query.example.v4.queries.insert.A2ExampleInsertPhoneV4
import gog.my_project.data_base.query.example.v4.queries.relations.UserPhoneQueryV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.then
import gog.my_project.data_base.query.executer.result.toCollection
import java.util.UUID

/** Commits dependent user/phone inserts and reads them through a typed CTE before and after commit. */
class A3ExampleSelectV4 : IExampleV4 {
    override fun execute(queryManager: IQueryBuilderExecutor) {
        val marker = "v4_tx_${UUID.randomUUID().toString().replace("-", "")}" 
        var userId: Int? = null
        var phoneId: Int? = null
        try {
            val ids = queryManager.transaction { tx ->
                val insertedUserId = Math.toIntExact(
                    tx.queryBuilder(A1ExampleInsertV4(marker, "${marker}_family", 34).query())
                        .sql { builtQuery -> println("V4 user INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                        .execute()
                        .success { key -> key }
                        .error { failure -> println("V4 user INSERT failed: ${failure.message}") }
                        .toValue(),
                ).also { userId = it }
                val insertedPhoneId = Math.toIntExact(
                    tx.queryBuilder(A2ExampleInsertPhoneV4(insertedUserId, "${marker}_phone").query())
                        .sql { builtQuery -> println("V4 phone INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                        .execute()
                        .success { key -> key }
                        .error { failure -> println("V4 phone INSERT failed: ${failure.message}") }
                        .toValue(),
                ).also { phoneId = it }

                UserPhoneQueryV4().execute(
                    tx,
                    UserPhoneQueryV4.RelationFilters(userId = insertedUserId),
                )
                    .success { rows ->
                        check(rows.firstOrNull { it.phoneId == insertedPhoneId } != null) {
                            "Inserted phone was not visible in transaction SELECT"
                        }
                        rows
                    }
                    .error { failure -> println("V4 in-transaction typed SELECT failed: ${failure.message}") }
                    .toCollection()

                ExecuteResult.Success(insertedUserId to insertedPhoneId)
            }
                .success { committedIds ->
                    println("V4 transaction committed generated user ${committedIds.first} and phone ${committedIds.second}")
                    committedIds
                }
                .error { failure -> println("V4 transaction failed: ${failure.message}") }
                .toValue()

            UserPhoneQueryV4().execute(
                queryManager,
                UserPhoneQueryV4.RelationFilters(userId = ids.first),
            )
                .success { rows ->
                    val found = rows.firstOrNull { it.id == ids.first && it.phoneId == ids.second } != null
                    println("V4 post-commit CTE readback verification=$found")
                    check(found) { "Committed user/phone pair was not visible after commit" }
                    rows
                }
                .error { failure -> println("V4 post-commit typed SELECT failed: ${failure.message}") }
                .toCollection()
        } finally {
            userId?.let { cleanupOwnRows(queryManager, it, phoneId) }
        }
    }
}

private fun cleanupOwnRows(queryManager: IQueryBuilderExecutor, userId: Int, phoneId: Int?) {
    val result = queryManager.transaction { tx ->
        val deletePhone = phoneId?.let { id ->
            tx.queryBuilder(A1ExampleDeleteV4().phoneQuery(userId, id))
                .execute()
                .then { deletedRows ->
                    deletedRows?.takeIf { it in 0..1 }
                        ?.let { ExecuteResult.Success(Unit) }
                        ?: ExecuteResult.Failure(IllegalStateException("V4 cleanup deleted an unexpected number of generated phone rows"))
                }
        } ?: ExecuteResult.Success(Unit)

        deletePhone.then {
            tx.queryBuilder(A1ExampleDeleteV4().userQuery(userId))
                .execute()
                .then { deletedRows ->
                    deletedRows?.takeIf { it in 0..1 }
                        ?.let { ExecuteResult.Success(Unit) }
                        ?: ExecuteResult.Failure(IllegalStateException("V4 cleanup deleted an unexpected number of generated user rows"))
                }
        }
    }
    result.success { println("V4 generated user/phone cleanup completed"); it }
        .error { failure -> println("V4 generated user/phone cleanup failed: ${failure.message}") }
}
