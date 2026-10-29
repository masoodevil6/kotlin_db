package gog.my_project.data_base.query.example.v4.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.delete.A1ExampleDeleteV4
import gog.my_project.data_base.query.example.v4.queries.insert.A1ExampleInsertV4
import gog.my_project.data_base.query.example.v4.queries.relations.UserDisplayQueryV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toCollection
import java.util.UUID

/** Executes a typed CTE SELECT on the transaction executor before commit, then verifies after commit. */
class A6ExampleSelectV4 : IExampleV4 {
    override fun execute(queryManager: IQueryBuilderExecutor) {
        val marker = "v4_sl_${UUID.randomUUID().toString().replace("-", "")}" 
        var userId: Int? = null
        try {
            queryManager.transaction { tx ->
                val id = Math.toIntExact(
                    tx.queryBuilder(A1ExampleInsertV4(marker, "${marker}_family", 37).query())
                        .sql { builtQuery -> println("V4 typed-SELECT fixture INSERT: ${builtQuery.query}; params=${builtQuery.params}") }
                        .execute()
                        .success { key -> key }
                        .error { failure -> println("V4 typed SELECT fixture INSERT failed: ${failure.message}") }
                        .toValue(),
                ).also { userId = it }

                val found = UserDisplayQueryV4().execute(
                    tx,
                    UserDisplayQueryV4.RelationFilters(userId = id),
                )
                    .success { rows -> rows.firstOrNull { it.id == id } != null }
                    .error { failure -> println("V4 in-transaction typed SELECT failed: ${failure.message}") }
                    .toValue()
                check(found) { "V4 typed transaction SELECT missed its generated user" }
                ExecuteResult.Success("typed SELECT found generated user $id inside transaction")
            }
                .success { message ->
                    println("V4 typed SELECT transaction succeeded: $message")
                    message
                }
                .error { failure -> println("V4 typed SELECT transaction failed: ${failure.message}") }
                .toValue()

            userId?.let { id ->
                UserDisplayQueryV4().execute(
                    queryManager,
                    UserDisplayQueryV4.RelationFilters(userId = id),
                )
                    .success { values ->
                        val found = values.firstOrNull { it.id == id } != null
                        println("V4 post-commit generated user verification=$found")
                        check(found) { "V4 post-commit typed read missed the committed user" }
                        values
                    }
                    .error { failure -> println("V4 post-commit typed read failed: ${failure.message}") }
                    .toCollection()
            }
        } finally {
            userId?.let { id ->
                queryManager.transaction { tx ->
                    tx.queryBuilder(A1ExampleDeleteV4().userQuery(id))
                        .execute()
                        .success { deletedRows -> deletedRows }
                }
                    .success { println("V4 typed SELECT fixture cleanup affected $it row(s)"); it }
                    .error { failure -> println("V4 typed SELECT fixture cleanup failed: ${failure.message}") }
            }
        }
    }
}
