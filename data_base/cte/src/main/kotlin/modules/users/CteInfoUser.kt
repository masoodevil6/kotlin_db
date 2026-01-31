package gog.my_project.data_base.cte.modules.users


import gog.my_project.cte.manager.BaseCte
import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.ctes.QBCteSelect
import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.query.reader.SqlParameter

@QBCte(
    name = "cte_info_user" ,
    alias = "cte_iu"
)
class CteInfoUser(
    val propUserId: Int
) :  BaseCte() , ICte {



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






   override fun cteQuery(params: MutableList<SqlParameter<*>>)/*: IQueryBuilder*/ {
       /* return QueryBuilder(params)
           .select {
               addColumn {
                   column {
                       column(Users::class ,Users::userId)
                   }
               }
               addColumn {
                   column {
                       column(Users::class ,Users::userName)
                   }
               }
               addColumn {
                   column {
                       column(Users::class ,Users::userFamily)
                   }
               }
               addColumn {
                   column {
                       column(Users::class ,Users::userAge)
                   }
               }
               addColumn {
                   column {
                       column(UserPhones::class ,UserPhones::userPhone)
                   }
               }
           }
           .table{
               table(Users::class)
           }
           .joins {
               addJoin {
                   innerJoin()
                   table {
                       table(UserPhones::class)
                   }
                   condition {
                       logicalOn()
                       addCondition {
                           sideSelector {
                               column(Users::class ,Users::userId)
                           }
                           operationEqual()
                           sideValue {
                               column(UserPhones::class ,UserPhones::userId)
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
                           column(Users::class ,Users::userId)
                       }
                       operationEqual()
                       sideValue(
                           "id1" ,
                           propUserId
                       )
                   }

               }
           }*/
   }


}