package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.query.example.v3.queries.models.UserPhoneModelV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

@QBQuery(name = "user_phone_query_v3", alias = "v3_user_phone_details")
class UserPhoneQueryV3 : CteDeclaration<UserPhoneQueryV3.RelationFilters, UserPhoneQueryV3>() {
    data class RelationFilters(
        val userId: Int? = null,
    )

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
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::id)
                }
                alias(UserPhoneQueryV3::id)
            }
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::name)
                }
                alias(UserPhoneQueryV3::name)
            }
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::family)
                }
                alias(UserPhoneQueryV3::family)
            }
            addColumn {
                column {
                    tableSelector(UserModelV3::class)
                    columnSelector(UserModelV3::age)
                }
                alias(UserPhoneQueryV3::age)
            }
            addColumn {
                column {
                    tableSelector(UserPhoneModelV3::class)
                    columnSelector(UserPhoneModelV3::phone)
                }
                alias(UserPhoneQueryV3::phone)
            }
        }
        from { model(UserModelV3::class) }
        joins {
            addJoin {
                innerJoin()
                table { table(UserPhoneModelV3::class) }
                condition {
                    logicalOn()
                    addCondition {
                        sideSelector {
                            tableSelector(UserModelV3::class)
                            columnSelector(UserModelV3::id)
                        }
                        operationEqual()
                        sideValueColumn {
                            tableSelector(UserPhoneModelV3::class)
                            columnSelector(UserPhoneModelV3::userId)
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
            }
        }
    }

}
