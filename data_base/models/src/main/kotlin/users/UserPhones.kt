package gog.my_project.data_base.models.users

import gog.my_project.data_base.annotations.models.QBColumn
import gog.my_project.data_base.annotations.models.QBTable
import gog.my_project.data_base.models.main.BaseModel

@QBTable(
    "user_phones" ,
    alias = "up"
)
class UserPhones() : BaseModel()
{

    @QBColumn(
        name= "id" ,
        primaryKey = true
    )
    val userPhoneId : Int =  0

    @QBColumn(
        name="phone"
    )
    val userPhone : String? =  null;

    @QBColumn(
        name="user_id"
    )
    val userId : Int = 0;

}