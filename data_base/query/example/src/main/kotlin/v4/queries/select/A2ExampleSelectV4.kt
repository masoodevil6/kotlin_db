package gog.my_project.data_base.query.example.v4.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.cte.mapper.CteQueryRowMapper
import gog.my_project.data_base.query.example.v4.queries.IExampleV4
import gog.my_project.data_base.query.example.v4.queries.relations.UserPhoneQueryV4
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toList

/** Demonstrates a typed CTE that joins phones to their owning users. */
class A2ExampleSelectV4 : IExampleV4 {
    fun query(): IQueryRenderSelectApi = buildQuery(UserPhoneQueryV4.RelationFilters(userId = 1))

    private fun buildQuery(params: UserPhoneQueryV4.RelationFilters): IQueryRenderSelectApi {
        val relation = UserPhoneQueryV4().createCte(params)
        val withName = "v4_user_phone_details"

        return QueryRenderSelectBuilder()
            .withs { addWith { with(withName, relation) } }
            .from {
                cte(withName)
                alias("phone_details")
            }
            .select {
                addColumn {
                    column {
                        tableSelector(UserPhoneQueryV4::class)
                        columnSelector(UserPhoneQueryV4::id)
                    }
                    alias("id")
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(UserPhoneQueryV4::class)
                        columnSelector(UserPhoneQueryV4::fullName)
                    }
                    alias("fullName")
                    execute(DataType.STRING)
                }
                addColumn {
                    column {
                        tableSelector(UserPhoneQueryV4::class)
                        columnSelector(UserPhoneQueryV4::age)
                    }
                    alias("age")
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(UserPhoneQueryV4::class)
                        columnSelector(UserPhoneQueryV4::phoneId)
                    }
                    alias("phoneId")
                    execute(DataType.INT)
                }
                addColumn {
                    column {
                        tableSelector(UserPhoneQueryV4::class)
                        columnSelector(UserPhoneQueryV4::phone)
                    }
                    alias("phone")
                    execute(DataType.STRING)
                }
            }
    }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        val examples = listOf(
            "phones for user id 1" to UserPhoneQueryV4.RelationFilters(userId = 1),
            "phone rows for users age 18+" to UserPhoneQueryV4.RelationFilters(minAge = 18),
        )
        queryManager.transaction { tx ->
            examples.forEach { (label, params) ->
                tx.queryBuilder(buildQuery(params))
                    .executeAs { row -> CteQueryRowMapper.map(UserPhoneQueryV4::class, row) }
                    .success { rows ->
                        println("V4 SELECT CTE ($label): ${rows.size} row(s)")
                        rows.forEach { row ->
                            println("row: id=${row.id}, fullName=${row.fullName}, age=${row.age}, phoneId=${row.phoneId}, phone=${row.phone}")
                        }
                        rows
                    }
                    .error { failure -> println("V4 phone CTE ($label) failed: ${failure.message}") }
                    .toList()
            }
            ExecuteResult.Success("all V4 phone CTE examples completed")
        }
            .success { message -> println("V4 phone SELECT transaction succeeded: $message"); message }
            .error { failure -> println("V4 phone SELECT transaction failed: ${failure.message}") }
            .toValue()
    }
}
