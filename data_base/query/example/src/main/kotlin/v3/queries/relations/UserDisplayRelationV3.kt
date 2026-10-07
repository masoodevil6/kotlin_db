package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.relations.QueryRelationDeclaration
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3

class UserDisplayRelationV3 : QueryRelationDeclaration<UserDisplayRelationV3.RelationFilters>() {
    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    override val relationName = "v3_user_display"

    val id: Int get() = error("Typed output property must not be read")
    val fullName: String? get() = error("Typed output property must not be read")
    val age: Int? get() = error("Typed output property must not be read")

    override fun queryRelation(params: RelationFilters): QueryRelation = buildQueryRelation {
        select {
            addColumn {
                column(UserModelV3::class, UserModelV3::id)
                    .alias(UserDisplayRelationV3::id)
            }
            addColumn {
                column { tableAttribute("concat( uu.name , '-' , uu.family )") }
                    .alias(UserDisplayRelationV3::fullName)
            }
            addColumn {
                column(UserModelV3::class, UserModelV3::age)
                    .alias(UserDisplayRelationV3::age)
            }
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
