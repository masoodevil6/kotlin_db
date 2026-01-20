package gog.my_project.data_base.models.users

@QBTable("user_users")
class Users()
{

    @QBColumn("id" , primaryKey = true)
    val userId : Int =  0

    @QBColumn("name")
    val userName : String? =  null;

    @QBColumn("family")
    val userFamily : String? =  null;

    @QBColumn("age")
    val userAge : String? =  null;

}