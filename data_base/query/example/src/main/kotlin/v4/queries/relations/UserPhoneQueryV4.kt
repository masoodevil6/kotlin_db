package gog.my_project.data_base.query.example.v4.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.example.v4.queries.models.UserModelV4
import gog.my_project.data_base.query.example.v4.queries.models.UserPhoneModelV4

@QBQuery(name = "user_phone_query_v4", alias = "v4_user_phone_details")
class UserPhoneQueryV4 : CteDeclaration<UserPhoneQueryV4.RelationFilters, UserPhoneQueryV4>() {
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

    @QBQuerySelect(name = "full_name", alias = "fullName")
    var fullName: String? = null

    @QBQuerySelect(name = "age", alias = "age")
    var age: Int? = null

    @QBQuerySelect(name = "phone_id", alias = "phoneId")
    var phoneId: Int? = null

    @QBQuerySelect(name = "phone", alias = "phone")
    var phone: String? = null

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn {
                column {
                    tableSelector(UserModelV4::class)
                    columnSelector(UserModelV4::id)
                }
                alias(UserPhoneQueryV4::id)
            }
            addColumn {
                methodConcat {
                    column {
                        tableSelector(UserModelV4::class)
                        columnSelector(UserModelV4::name)
                    }
                    string("-")
                    column {
                        tableSelector(UserModelV4::class)
                        columnSelector(UserModelV4::family)
                    }
                }
                alias(UserPhoneQueryV4::fullName)
                execute(DataType.STRING)
            }
            addColumn {
                column {
                    tableSelector(UserModelV4::class)
                    columnSelector(UserModelV4::age)
                }
                alias(UserPhoneQueryV4::age)
            }
            addColumn {
                column {
                    tableSelector(UserPhoneModelV4::class)
                    columnSelector(UserPhoneModelV4::id)
                }
                alias(UserPhoneQueryV4::phoneId)
            }
            addColumn {
                column {
                    tableSelector(UserPhoneModelV4::class)
                    columnSelector(UserPhoneModelV4::phone)
                }
                alias(UserPhoneQueryV4::phone)
            }
        }
        from { model(UserModelV4::class) }
        joins {
            addJoin {
                innerJoin()
                table { table(UserPhoneModelV4::class) }
                condition {
                    logicalOn()
                    addCondition {
                        sideSelector {
                            tableSelector(UserModelV4::class)
                            columnSelector(UserModelV4::id)
                        }
                        operationEqual()
                        sideValueColumn {
                            tableSelector(UserPhoneModelV4::class)
                            columnSelector(UserPhoneModelV4::userId)
                        }
                    }
                }
            }
        }
        if (params.userId != null || params.minAge != null) {
            where {
                conditions {
                    params.userId?.let { userId ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(UserModelV4::class)
                                columnSelector(UserModelV4::id)
                            }
                            operationEqual()
                            sideValueParam {
                                paramName("v4_phone_user_id")
                                paramValue(userId)
                            }
                        }
                    }
                    params.minAge?.let { minAge ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableSelector(UserModelV4::class)
                                columnSelector(UserModelV4::age)
                            }
                            operationGreaterThanOrEqual()
                            sideValueParam {
                                paramName("v4_phone_min_age")
                                paramValue(minAge)
                            }
                        }
                    }
                }
            }
        }
    }
}
