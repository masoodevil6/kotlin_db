package gog.kotlin_db.data.models.user

import gog.kotlin_db.data.base.schemas.QBColumn
import gog.kotlin_db.data.base.schemas.QBTable


@QBTable("user_phones")
class UserPhones()
{

    @QBColumn("id" , primaryKey = true)
    val userPhoneId : Int =  0

    @QBColumn("phone")
    val userPhone : String? =  null;

    @QBColumn("user_id")
    val userId : Int = 0;

}