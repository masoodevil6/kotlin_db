package gog.my_project.data_base.query.example.v3_1.queries.relations

import gog.my_project.collection.ICollection
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata
import gog.my_project.data_base.query.example.v3_1.queries.models.UserModelV31
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryConsumer
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

@QBQuery(name = "user_display_query_v31", alias = "v31_user_display")
class UserDisplayQueryV31 : CteDeclaration<UserDisplayQueryV31.RelationFilters, UserDisplayQueryV31>() {
    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    private var mappedId: Int? = null

    @QBQuerySelect(name = "id", alias = "id")
    var id: Int
        get() = mappedId
            ?: throw IllegalStateException("Output property 'id' has not been mapped")
        set(value) {
            mappedId = value
        }

    @QBQuerySelect(name = "full_name", alias = "fullName")
    var fullName: String? = null

    @QBQuerySelect(name = "age", alias = "age")
    var age: Int? = null

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::id) }
                alias(UserDisplayQueryV31::id)
            }
            addColumn {
                methodConcat {
                    column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::name) }
                    string("-")
                    column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::family) }
                }
                alias(UserDisplayQueryV31::fullName)
                execute(DataType.STRING)
            }
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::age) }
                alias(UserDisplayQueryV31::age)
            }
        }
        from { model(UserModelV31::class) }
        if (params.userId != null || params.minAge != null) {
            where {
                conditions {
                    params.userId?.let { userId ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(UserModelV31::class)
                                columnSelector(UserModelV31::id)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("relation_user_id")
                                paramValue(userId)
                            }
                        }
                    }
                    params.minAge?.let { minAge ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(UserModelV31::class)
                                columnSelector(UserModelV31::age)
                            }
                            operationGreaterThanOrEqual()
                            sideValueParam {
                                paramName("relation_min_age")
                                paramValue(minAge)
                            }
                        }
                    }
                }
            }
        }
    }


    override fun execute(
        queryManager: IQueryBuilderExecutor,
        params: RelationFilters
    ): ExecuteResult<ICollection<UserDisplayQueryV31>> {
        println("\n=============================================\nV3.1: typed callbacks inside UserDisplayQueryV31.execute")
        return super.execute(queryManager, params)
            .success { rows ->
                rows.forEach { row ->
                    val lastName = row.fullName;
                    row.fullName = "aaaaa";
                    println("row: id=${row.id}, fullName=${row.fullName}, age=${row.age}")
                }
                rows
            }
            .error { failure -> println("error: $failure") }
    }

    /*protected override fun configureConsumer(consumer: QueryConsumer): QueryConsumer =
        consumer
            .sql { (queryText, params) ->
                println("\n=============================================\nV3-Ex1: WITH CTE select with phone join\nquery: $queryText\nparams: $params")
            }
            .details { metadata ->
                println(formatEffectiveQueryMetadata(metadata))
            }*/

}
