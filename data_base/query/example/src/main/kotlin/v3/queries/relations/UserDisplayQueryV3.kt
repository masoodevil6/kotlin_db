package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success


@QBQuery(name = "user_display_query_v3", alias = "v3_user_display")
class UserDisplayQueryV3 : CteDeclaration<UserDisplayQueryV3.RelationFilters, UserDisplayQueryV3>() {


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
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::id)
                }
                alias(UserDisplayQueryV3::id)
            }
            addColumn {
                methodConcat {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::name)
                    }
                    string("-")
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::family)
                    }
                }
                alias(UserDisplayQueryV3::fullName)
                execute(DataType.STRING)
            }
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::age)
                }
                alias(UserDisplayQueryV3::age)
            }
        }
        from {
            model(UserModelV3::class)
        }
        if (params.userId != null || params.minAge != null) {
            where {
                conditions {
                    params.userId?.let { userId ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(UserModelV3::class)
                                columnSelector(UserModelV3::id)
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
                                tableSelector(UserModelV3::class)
                                columnSelector(UserModelV3::age)
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

}
