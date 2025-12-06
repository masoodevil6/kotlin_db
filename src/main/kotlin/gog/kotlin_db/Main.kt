package gog.kotlin_db.data

import gog.kotlin_db.data.base.DataBase
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.models.user.Users
import java.util.logging.LogManager

fun main() {
    LogManager.getLogManager().reset()

    val db = DataBase()
    println("Connected: ${db.connection?.isValid(2)}")


    var query = QueryBuilder()
        .column(Users::id.name)
        .column(Users::name.name)
        /*.column(Users::name.name , "abs")
        .column( "abs"){
            queryBuilder ->
            queryBuilder
                .column(Users::name.name)
                .table(Users::class.simpleName)
        }*/
        //.sum(Users::age.name , "sum")
       // .table(Users::class.simpleName)
        .table(Users.tableName)
        .where(){ schema->
            schema
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
        .toSql();

    println(query)




}