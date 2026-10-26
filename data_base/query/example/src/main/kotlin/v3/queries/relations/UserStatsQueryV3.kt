package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3

@QBQuery(name = "user_stats_query_v3", alias = "v3_user_stats")
class UserStatsQueryV3 : CteDeclaration<UserStatsQueryV3.RelationFilters, UserStatsQueryV3>() {
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
    @QBQuerySelect(name = "name", alias = "name")
    var name: String? = null
    @QBQuerySelect(name = "family", alias = "family")
    var family: String? = null
    @QBQuerySelect(name = "age", alias = "age")
    var age: Int? = null

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::id)
                }
                alias(UserStatsQueryV3::id)
            }
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::name)
                }
                alias(UserStatsQueryV3::name)
            }
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::family)
                }
                alias(UserStatsQueryV3::family)
            }
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::age)
                }
                alias(UserStatsQueryV3::age)
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
