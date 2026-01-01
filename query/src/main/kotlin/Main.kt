package gog.my_project

import gog.my_project.core.sql_dialect.DialectQuery
import gog.my_project.data_base.base.DatabaseBuilder
import gog.my_project.query.query_builder.QueryContext
import java.sql.Connection
import java.util.logging.LogManager

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    LogManager.getLogManager().reset()



    val dbBuilder =  DatabaseBuilder()
        .config {
            domain("jdbc:mysql://127.0.0.1")
            port(3306)
            name("kotlin_db")
            username("root")
            password("")
        }
        .dialect(DialectQuery.MY_SQL);



    val db: Connection = dbBuilder.build();
    println("Connected: ${db.isValid(2)}")



    val queryBuilder = QueryContext(dbBuilder.getDialect()).createQueryBuilder();
    var query = queryBuilder
        .withs{
            addWith {
                withName("users")
                withBody {
                    select {
                        addColumn {
                            column("uu.id")
                        }
                        addColumn {
                            column("uu.name")
                        }
                        addColumn {
                            column("uu.family")
                        }
                        addColumn {
                            column("uu.age")
                        }
                        addColumn {
                            column("up.phone")
                        }
                    }
                    table{
                        alias("uu")
                        table("user_users")
                    }
                    joins {
                        addJoin {
                            innerJoin()
                            tableJoin {
                                alias("up")
                                table("user_phones")
                            }
                            conditionJoin {
                                addCondition {
                                    logicalOn()
                                   // sideLeft("uu.id")
                                    sideLeft {
                                        columnPrefix("uu")
                                        columnName("id")
                                    }
                                    operationEqual()
                                    sideRight("up.user_id")
                                    sideRight {
                                        columnPrefix("up")
                                        columnName("user_id")
                                    }
                                    //sideRight("up.user_id")
                                }
                            }
                        }
                    }
                    where{
                        addCondition {
                            logicalAnd();
                            sideLeft("uu.id")
                            sideLeft {
                                columnPrefix("uu")
                                columnName("id")
                            }
                            operationEqual()
                            sideRight(1.toString())
                        }
                        addCondition {
                            logicalAnd();
                            sideLeft {
                                columnPrefix("uu")
                                columnName("id")
                            }
                            //sideLeft("uu.id")
                            operationIsNotNull();
                        }
                    }
                }
            }
        }
        .select{
            addColumn{
                column {
                    columnPrefix("u")
                    columnName("id")
                }
            }
            addColumn{
                column {
                    columnPrefix("u")
                    columnName("name")
                }
            }
            addColumn{
                column {
                    columnPrefix("u")
                    columnName("family")
                }
            }
            addColumn{
                column {
                    columnPrefix("u")
                    columnName("age")
                }
            }
            addColumn{
                column {
                    columnPrefix("u")
                    columnName("phone")
                }
            }
        }
        .table{
            table("users")
            alias("u")
        }
        .limit(2)
        .offset(0)
        .group {
            addColumn{
                columnPrefix("u")
                columnName("id")
            }
            addColumn{
                columnPrefix("u")
                columnName("name")
            }
            addColumn{
                columnPrefix("u")
                columnName("family")
            }
            addColumn{
                columnPrefix("u")
                columnName("age")
            }
            addColumn{
                columnPrefix("u")
                columnName("phone")
            }
        }
        .order {
            typeAsc()
            addColumn{
                columnPrefix("u")
                columnName("id")
            }
        }
    println(query.toSqlReadable())



    val queryCreated : String? = query.toSql();
    dbBuilder.fetch(queryCreated){
            result->

        if (result != null){

            var number = 1;
            while (result.next()) {

                val id = result.getInt("id")
                val name = result.getString("name")
                val family = result.getString("family")
                val age = result.getString("age")
                val phone = result.getString("phone")

                println("User: $number - $id $name $family $age $phone");
                number ++;
            }
        }
    }

}