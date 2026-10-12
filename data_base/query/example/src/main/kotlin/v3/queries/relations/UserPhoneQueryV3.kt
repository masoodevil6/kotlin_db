package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.query.example.v3.queries.models.UserPhoneModelV3

@QBQuery(name = "user_phone_query_v3", alias = "v3_user_phone_details")
class UserPhoneQueryV3(
    override val params: RelationFilters,
) : ICteDeclaration<UserPhoneQueryV3.RelationFilters> {
    data class RelationFilters(
        val userId: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "user_id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "user_name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "user_family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "user_age")
    val age: Int? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "phone", alias = "user_phone")
    val phone: String? get() = error("Typed output property must not be read")

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn { column(UserModelV3::class, UserModelV3::id).alias(UserPhoneQueryV3::id) }
            addColumn { column(UserModelV3::class, UserModelV3::name).alias(UserPhoneQueryV3::name) }
            addColumn { column(UserModelV3::class, UserModelV3::family).alias(UserPhoneQueryV3::family) }
            addColumn { column(UserModelV3::class, UserModelV3::age).alias(UserPhoneQueryV3::age) }
            addColumn { column(UserPhoneModelV3::class, UserPhoneModelV3::phone).alias(UserPhoneQueryV3::phone) }
        }
        from { model(UserModelV3::class) }
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
