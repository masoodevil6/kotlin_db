package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteDeclaration
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3




@QBQuery(name = "user_display_query_v3", alias = "v3_user_display")
class UserDisplayQueryV3(
    params: RelationFilters,
) : CteDeclaration<UserDisplayQueryV3.RelationFilters>(params) {

    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "full_name", alias = "fullName")
    val fullName: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column(UserModelV3::class, UserModelV3::id)
                    .alias(UserDisplayQueryV3::id)
            }
            addColumn {
                column { tableAttribute("concat( uu.name , '-' , uu.family )") }
                    .alias(UserDisplayQueryV3::fullName)
            }
            addColumn {
                column(UserModelV3::class, UserModelV3::age)
                    .alias(UserDisplayQueryV3::age)
            }
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
