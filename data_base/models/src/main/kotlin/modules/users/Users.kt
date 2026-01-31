package gog.my_project.data_base.models.modules.users

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.models.manager.BaseModel

@QBTable(
    name="user_users" ,
    alias = "uu"
)
class Users() : BaseModel() , IModel
{

    @QBColumn(
        name="id" ,
        alias = "u_user_id",
        primaryKey = true
    )
    val userId : Int =  0

    @QBColumn(
        name="name" ,
        alias = "u_user_name",
    )
    val userName : String? =  null;

    @QBColumn(
        name="family" ,
        alias = "u_user_family",
    )
    val userFamily : String? =  null;

    @QBColumn(
        name="age" ,
        alias = "u_user_age",
    )
    val userAge : String? =  null;

}