package gog.my_project.data_base.query.example.v3_1.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.example.v3_1.queries.models.UserModelV31
import gog.my_project.data_base.query.example.v3_1.queries.models.UserPhoneModelV31

@QBQuery(name = "user_phone_query_v31", alias = "v31_user_phone_details")
class UserPhoneQueryV31 : CteDeclaration<UserPhoneQueryV31.RelationFilters, UserPhoneQueryV31>() {
    data class RelationFilters(val userId: Int? = null)

    private var mappedId: Int? = null

    @QBQuerySelect(name = "id", alias = "user_id")
    var id: Int
        get() = mappedId
            ?: throw IllegalStateException("Output property 'id' has not been mapped")
        set(value) {
            mappedId = value
        }

    @QBQuerySelect(name = "name", alias = "user_name")
    var name: String? = null

    @QBQuerySelect(name = "family", alias = "user_family")
    var family: String? = null

    @QBQuerySelect(name = "age", alias = "user_age")
    var age: Int? = null

    @QBQuerySelect(name = "phone", alias = "user_phone")
    var phone: String? = null

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::id) }
                alias(UserPhoneQueryV31::id)
            }
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::name) }
                alias(UserPhoneQueryV31::name)
            }
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::family) }
                alias(UserPhoneQueryV31::family)
            }
            addColumn {
                column { tableSelector(UserModelV31::class); columnSelector(UserModelV31::age) }
                alias(UserPhoneQueryV31::age)
            }
            addColumn {
                column { tableSelector(UserPhoneModelV31::class); columnSelector(UserPhoneModelV31::phone) }
                alias(UserPhoneQueryV31::phone)
            }
        }
        from { model(UserModelV31::class) }
        joins {
            addJoin {
                innerJoin()
                table { table(UserPhoneModelV31::class) }
                condition {
                    logicalOn()
                    addCondition {
                        sideSelector {
                            tableSelector(UserModelV31::class)
                            columnSelector(UserModelV31::id)
                        }
                        operationEqual()
                        sideValueColumn {
                            tableSelector(UserPhoneModelV31::class)
                            columnSelector(UserPhoneModelV31::userId)
                        }
                    }
                }
            }
        }
        params.userId?.let { userId ->
            where {
                conditions {
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
            }
        }
    }
}
