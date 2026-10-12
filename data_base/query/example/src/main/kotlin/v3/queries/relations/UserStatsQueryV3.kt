package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3

@QBQuery(name = "user_stats_query_v3", alias = "v3_user_stats")
class UserStatsQueryV3(
    override val params: RelationFilters,
) : ICteDeclaration<UserStatsQueryV3.RelationFilters> {
    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn { column(UserModelV3::class, UserModelV3::id).alias(UserStatsQueryV3::id) }
            addColumn { column(UserModelV3::class, UserModelV3::name).alias(UserStatsQueryV3::name) }
            addColumn { column(UserModelV3::class, UserModelV3::family).alias(UserStatsQueryV3::family) }
            addColumn { column(UserModelV3::class, UserModelV3::age).alias(UserStatsQueryV3::age) }
        }
        from { model(UserModelV3::class) }
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
