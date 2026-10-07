package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.relations.buildQueryRelation
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3

class UserStatsRelationV3 : IQueryRelation<UserStatsRelationV3.RelationFilters> {
    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    override val relationName = "v3_user_stats"

    val id: Int get() = error("Typed output property must not be read")
    val name: String? get() = error("Typed output property must not be read")
    val family: String? get() = error("Typed output property must not be read")
    val age: Int? get() = error("Typed output property must not be read")

    override fun queryRelation(params: RelationFilters): QueryRelation = buildQueryRelation {
        select {
            addColumn { column(UserModelV3::class, UserModelV3::id).alias(UserStatsRelationV3::id) }
            addColumn { column(UserModelV3::class, UserModelV3::name).alias(UserStatsRelationV3::name) }
            addColumn { column(UserModelV3::class, UserModelV3::family).alias(UserStatsRelationV3::family) }
            addColumn { column(UserModelV3::class, UserModelV3::age).alias(UserStatsRelationV3::age) }
        }
        table { table(UserModelV3::class) }
        if (params.userId != null || params.minAge != null) {
            where {
                conditions {
                    params.userId?.let { userId ->
                        addCondition {
                            logicalAnd()
                            sideSelector { tableColumn(UserModelV3::class, UserModelV3::id, "uu") }
                            operationEqual()
                            sideValue("relation_user_id", userId)
                        }
                    }
                    params.minAge?.let { minAge ->
                        addCondition {
                            logicalAnd()
                            sideSelector { tableColumn(UserModelV3::class, UserModelV3::age, "uu") }
                            operationGreaterThanOrEqual()
                            sideValue("relation_min_age", minAge)
                        }
                    }
                }
            }
        }
    }
}
