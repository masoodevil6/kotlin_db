package gog.my_project.data_base.query.example.v4.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.cte.mapper.CteQueryRowMapper
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.relations.UserDisplayQueryV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toCollection

/** Demonstrates named WITH composition over a filtered, typed CTE declaration. */
class A1ExampleSelectV4 : IExampleV4 {
    fun query(): IQueryRenderSelectApi = buildQuery(UserDisplayQueryV4.RelationFilters())

    private fun buildQuery(params: UserDisplayQueryV4.RelationFilters): IQueryRenderSelectApi {
        val relation = UserDisplayQueryV4().createCte(params)
        val withName = "v4_filtered_user_display"

        return QueryRenderSelectBuilder()
            .withs { addWith { with(withName, relation) } }
            .from {
                cte(withName)
                alias("users")
            }
            .select {
                addColumn {
                    column {
                        tableSelector(UserDisplayQueryV4::class)
                        columnSelector(UserDisplayQueryV4::id)
                    }
                    alias("id")
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(UserDisplayQueryV4::class)
                        columnSelector(UserDisplayQueryV4::fullName)
                    }
                    alias("fullName")
                    execute(DataType.STRING)
                }
                addColumn {
                    column {
                        tableSelector(UserDisplayQueryV4::class)
                        columnSelector(UserDisplayQueryV4::age)
                    }
                    alias("age")
                    execute(DataType.INT)
                }
            }
    }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val filterExamples = listOf(
            "all users" to UserDisplayQueryV4.RelationFilters(),
            "user id 1" to UserDisplayQueryV4.RelationFilters(userId = 1),
            "minimum age 18" to UserDisplayQueryV4.RelationFilters(minAge = 18),
            "user id 1 and minimum age 18" to UserDisplayQueryV4.RelationFilters(userId = 1, minAge = 18),
        )

        queryManager.transaction { tx ->
            filterExamples.forEach { (label, params) ->
                tx.queryBuilder(buildQuery(params))
                    .executeAs { row -> CteQueryRowMapper.map(UserDisplayQueryV4::class, row) }
                    .success { rows ->
                        println("V4 SELECT CTE ($label): ${rows.size} row(s)")
                        rows.forEach { row ->
                            println("row: id=${row.id}, fullName=${row.fullName}, age=${row.age}")
                        }
                        rows
                    }
                    .error { failure -> println("V4 SELECT CTE ($label) failed: ${failure.message}") }
                    .toCollection()
            }
            ExecuteResult.Success("all V4 user CTE filter examples completed")
        }
            .success { message -> println("V4 SELECT transaction succeeded: $message"); message }
            .error { failure -> println("V4 SELECT transaction failed: ${failure.message}") }
            .toValue()
    }
}
