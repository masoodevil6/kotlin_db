package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.relations.buildQueryRelation
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.query.example.v3.queries.models.UserPhoneModelV3

class UserPhoneRelationV3 : IQueryRelation<UserPhoneRelationV3.RelationFilters> {
    data class RelationFilters(
        val userId: Int? = null,
    )

    override val relationName = "v3_user_phone_details"

    val id: Int get() = error("Typed output property must not be read")
    val name: String? get() = error("Typed output property must not be read")
    val family: String? get() = error("Typed output property must not be read")
    val age: Int? get() = error("Typed output property must not be read")
    val phone: String? get() = error("Typed output property must not be read")

    override fun queryRelation(params: RelationFilters): QueryRelation = buildQueryRelation {
        select {
            addColumn { column(UserModelV3::class, UserModelV3::id).alias(UserPhoneRelationV3::id) }
            addColumn { column(UserModelV3::class, UserModelV3::name).alias(UserPhoneRelationV3::name) }
            addColumn { column(UserModelV3::class, UserModelV3::family).alias(UserPhoneRelationV3::family) }
            addColumn { column(UserModelV3::class, UserModelV3::age).alias(UserPhoneRelationV3::age) }
            addColumn { column(UserPhoneModelV3::class, UserPhoneModelV3::phone).alias(UserPhoneRelationV3::phone) }
        }
        table { table(UserModelV3::class) }
        joins {
            addJoin {
                innerJoin()
                table { table(UserPhoneModelV3::class) }
                condition {
                    logicalOn()
                    addCondition {
                        sideSelector { tableColumn(UserModelV3::class, UserModelV3::id, "uu") }
                        operationEqual()
                        sideValue { tableColumn(UserPhoneModelV3::class, UserPhoneModelV3::userId, "up") }
                    }
                }
            }
        }
        params.userId?.let { userId ->
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn(UserModelV3::class, UserModelV3::id, "uu") }
                        operationEqual()
                        sideValue("relation_user_id", userId)
                    }
                }
            }
        }
    }
}
