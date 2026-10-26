package gog.my_project.data_base.query.example.v3_1.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.example.v3_1.queries.models.UserModelV31

@QBQuery(name = "user_stats_query_v31", alias = "v31_user_stats")
class UserStatsQueryV31 : CteDeclaration<UserStatsQueryV31.RelationFilters, UserStatsQueryV31>() {
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
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::id) }
                alias(UserStatsQueryV31::id)
            }
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::name) }
                alias(UserStatsQueryV31::name)
            }
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::family) }
                alias(UserStatsQueryV31::family)
            }
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::age) }
                alias(UserStatsQueryV31::age)
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
}
