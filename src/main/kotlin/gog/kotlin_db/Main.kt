package gog.kotlin_db.data

import gog.kotlin_db.data.base.DataBase
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.tools.with.QueryTools_withsCollection
import gog.kotlin_db.data.models.user.Users
import java.util.logging.LogManager

fun main() {
    LogManager.getLogManager().reset()

    val db = DataBase()
    println("Connected: ${db.connection?.isValid(2)}")


    var query = QueryBuilder()
        .withs{schema->
            schema
                .with("users") { schema ->
                    schema
                        .select { schema->
                            schema
                                .column("uu.id")
                                .column("uu.name")
                                .column("uu.family")
                                .column("uu.age")
                                .column("up.phone")
                        }
                        .table{ schema->
                            schema.table("user_users" , "uu")
                        }
                        .joins{ schema ->
                            schema.join("user_phones" , "up"){condition->
                                condition.whereAnd("uu.id" , "=" , "up.user_id" )
                            }
                        }
                        .where{ schema->
                            schema.whereAnd("uu.id" , "="  , 1.toString())
                                .whereAnd("uu.id" , "="  , null)
                                .whereNull( "and" , "uu.id" )
                        }
                }
        }
        .select{
            schema->
            schema
                .column("u.id")
                .column("u.name")
                .column("u.family")
                .column("u.age")
                .column("u.phone")
        }
        .table{ schema->
            schema.table("users" , "u")
        }
        .options{
            schema->
            schema
                .pageInit(1 , 2)
        };

    //println(query.toSql())
    println(query.toSqlReadable())

  /*  var query = QueryBuilder()
        .select{
            schema->
            schema
                .column(Users::id.name)
                .column(Users::name.name)
        }
        .table{
            schema->
            schema.table(Users.tableName)
        }
        .joins{ schema ->
            schema
                .join("user_phones"){condition->
                    condition.whereAnd("user_users.id" , "=" , "user_phones.user_id" )
                }
        }
        .where{ schema->
            schema
                .whereLike(Users::name.name , "aaaa")
                .group("and"){ schema->
                    schema
                        .whereAnd(
                            Users.tableName+"."+ Users::id.name,
                            "=" ,
                            1.toString()
                        )
                        .whereAnd(
                            Users.tableName+"."+ Users::name.name,
                            "like" ,
                            "'مهدی'"
                        )
                }
                .group("or"){ schema->
                    schema
                        .whereAnd(
                            Users.tableName+"."+ Users::id.name,
                            "=" ,
                            2.toString()
                        )
                        .whereAnd(
                            Users.tableName+"."+ Users::name.name,
                            "like" ,
                            "'عباس '"
                        )
                }
        }
        .options{
            schema->
            schema
                .pageInit(1 , 2)
                .addGroup(Users::name.name )
        }
        .toSql();

    println(query)*/




}