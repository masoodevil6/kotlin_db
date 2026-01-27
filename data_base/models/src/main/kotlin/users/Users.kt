package gog.my_project.data_base.models.users

import gog.my_project.data_base.annotations.models.QBColumn
import gog.my_project.data_base.annotations.models.QBTable
import gog.my_project.data_base.models.main.BaseModel

@QBTable(
    name="user_users" ,
    alias = "uu"
)
class Users() : BaseModel()
{

    @QBColumn(
        name="id" ,
        primaryKey = true
    )
    val userId : Int =  0

    @QBColumn(
        name="name"
    )
    val userName : String? =  null;

    @QBColumn(
        name="family"
    )
    val userFamily : String? =  null;

    @QBColumn(
        name="age"
    )
    val userAge : String? =  null;

}