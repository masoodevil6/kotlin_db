package  gog.my_project.data_base.models.eloquent.modules.users

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.models.eloquent.manager.BaseModel


@QBTable(
    "user_phones" ,
    alias = "up"
)
class UserPhones() : BaseModel()
{

    @QBColumn(
        name= "id" ,
        alias = "up_id",
        primaryKey = true
    )
    val userPhoneId : Int =  0

    @QBColumn(
        name="phone" ,
        alias = "up_phone",
    )
    val userPhone : String? =  null;

    @QBColumn(
        name="user_id" ,
        alias = "up_user_id",
    )
    val userId : Int = 0;

}