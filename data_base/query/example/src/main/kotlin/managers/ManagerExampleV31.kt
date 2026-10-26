package gog.my_project.data_base.query.example.managers

import gog.my_project.collection.ICollection
import gog.my_project.data_base.query.example.v3_1.queries.execute.CteExecutionExampleV31
import gog.my_project.data_base.query.example.v3_1.queries.IExampleV31
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserDisplayQueryV31
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserPageQueryV31
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserPhoneQueryV31
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserStatsQueryV31
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.toCollection

class ManagerExampleV31(
    private val statusRunSelect: Boolean = true,
) {
    private val examples: List<IExampleV31> = listOf(
        CteExecutionExampleV31(
            title = "UserPhone",
            execution = { manager ->
                UserPhoneQueryV31().execute(manager, UserPhoneQueryV31.RelationFilters(userId = 1))
            },
            formatRow = { row ->
                "id=${row.id}, " +
                    "name=${row.name}, " +
                    "family=${row.family}, " +
                    "age=${row.age}, " +
                    "phone=${row.phone}"
            },
        ),
        object : IExampleV31 {
            override fun execute(queryManager: IQueryBuilderExecutor) {
                UserDisplayQueryV31().execute(queryManager, UserDisplayQueryV31.RelationFilters())
            }
        },
        CteExecutionExampleV31(
            title = "UserStats",
            execution = { manager ->
                UserStatsQueryV31().execute(manager, UserStatsQueryV31.RelationFilters())
            },
            formatRow = { row ->
                "id=${row.id}, " +
                    "name=${row.name}, " +
                    "family=${row.family}, " +
                    "age=${row.age}"
            },
        ),
        object : IExampleV31 {
            override fun execute(queryManager: IQueryBuilderExecutor) {
                println("\n=============================================\nV3.1: typed CTE result extracted as ICollection")
                val rows: ICollection<UserPageQueryV31> = UserPageQueryV31()
                    .execute(queryManager, UserPageQueryV31.RelationFilters())
                    .toCollection()
                rows.forEach { row ->
                    println("row: id=${row.id}, name=${row.name}, family=${row.family}, age=${row.age}")
                }
            }
        },
    )

    fun renderExamples(queryManager: IQueryBuilderExecutor) {
        if (!statusRunSelect) return

        examples.forEach { example ->
            try {
                example.execute(queryManager)
            } catch (failure: Exception) {
                println("Example ${example::class.simpleName} failed: ${failure.message}")
            }
        }
    }
}
