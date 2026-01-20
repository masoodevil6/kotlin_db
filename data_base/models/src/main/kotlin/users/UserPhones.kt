package gog.my_project.data_base.models.users

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