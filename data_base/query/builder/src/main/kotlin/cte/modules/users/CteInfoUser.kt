package gog.my_project.data_base.query.builder.cte.modules.users

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.ctes.QBCteSelect
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.models.eloquent.modules.users.UserPhones
import gog.my_project.data_base.models.eloquent.modules.users.Users
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder

@QBCte(
    name = "cte_info_user" ,
    alias = "cte_iu"
)
class CteInfoUser(
    val propUserId: Int
) : ICte {

    @QBCteSelect(
        name= "u_user_id",
        alias = "cte_user_id"
    )
    val cteUserId : Int =  0

    @QBCteSelect(
        name= "u_user_name",
        alias = "cte_user_name"
    )
    val cteUserName : Int =  0

    @QBCteSelect(
        name= "u_user_family",
        alias = "cte_user_family"
    )
    val cteUserFamily : Int =  0

    @QBCteSelect(
        name= "u_user_age",
        alias = "cte_user_age"
    )
    val cteUserAge : Int =  0


    @QBCteSelect(
        name= "up_phone",
        alias = "cte_user_phone"
    )
    val userPhone : Int =  0


    override fun cteQuery(params: MutableList<SqlParameter<*>>): gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi {
        return _root_ide_package_.gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder(
            params
        )
            .select {
                addColumn {
                    column {
                        tableColumn("uu" , "id")
                    }
                    alias("cte_user_id")
                }
                addColumn {
                    column {
                        tableColumn("uu" , "name")
                    }
                    alias("cte_user_name")
                }
                addColumn {
                    column {
                        tableColumn("uu" , "family")
                    }
                    alias("cte_user_family")
                }
                addColumn {
                    column {
                        tableColumn("uu" , "age")
                    }
                    alias("cte_user_age")
                }
                addColumn {
                    column {
                        tableColumn("up" , "phone")
                    }
                    alias("cte_user_phone")
                }
            }
            .table{
                table("user_users" , "uu")
            }
            .joins {
                addJoin {
                    innerJoin()
                    table {
                        table("user_phones" , "up")
                    }
                    condition {
                        logicalOn()
                        addCondition {
                            sideSelector {
                                tableColumn("uu" , "id")
                            }
                            operationEqual()
                            sideValue {
                                tableColumn("up" , "user_id")
                            }
                        }
                    }
                }
            }
            .where{
                conditions {

                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableColumn("uu" , "id")
                        }
                        operationEqual()
                        sideValue(
                            "id1" ,
                            propUserId
                        )
                    }

                }
            }
    }





}