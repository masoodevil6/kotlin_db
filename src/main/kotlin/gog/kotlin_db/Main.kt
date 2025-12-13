package gog.kotlin_db.data

import gog.kotlin_db.data.base.database.DataBase
import gog.kotlin_db.data.base.query_builder.QueryBuilder
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
                             //   .whereNull( "and" , "uu.id" )
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
        .limit(2)
        .offset(0)
        .group {schema->
            schema
                .addColumn("id")
                .addColumn("name")
                .addColumn("family")
                .addColumn("age")
                .addColumn("phone")
        }
        .order {scehma->
            scehma
                .type("asc")
                .addColumn("id")

        }


   // println(query.toSql())
    println(query.toSqlReadable())


    val queryCreated : String? = query.toSql();


    db.fetch(queryCreated){
        result->

        if (result != null){

            while (result.next()) {

                val id = result.getInt("id")
                val name = result.getString("name")
                val family = result.getString("family")
                val age = result.getString("age")
                val phone = result.getString("phone")

                println("User: $id - $name $family $age $phone")
            }
        }
    }


}