package gog.kotlin_db.data.models.user

import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

class Users()/*: QueryBuilder ()*/{

    companion object {
        val tableName : String= "user_users"
    }

    val id : Int =  0
    val name : String? =  null;
    val family : String? =  null;
    val age : String? =  null;

    /*init {
        setTableName(Users.tableName);
    }*/


}