package gog.my_project

import gog.my_project.core.sql_dialect.DialectQuery
import gog.my_project.data_base.base.DatabaseBuilder
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.QueryBuilder
import gog.my_project.query.query_builder.QueryContext
import gog.my_project.query.query_builder.tools.table.QueryToolsTable
import gog.my_project.query.sql_dialect.SqlDialectFactory
import java.sql.Connection
import java.sql.PreparedStatement
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

    val sqlDialect: ISqlDialect = SqlDialectFactory().create(dbBuilder.getDialect())



    val db: Connection = dbBuilder.build();
    println("Connected: ${db.isValid(2)}")


    //val queryBuilder = QueryContext(dbBuilder.getDialect()).createQueryBuilder();

    var query = QueryBuilder()
        .withs{
            addWith {
                withName("users")
                withBody {
                    select {
                        addColumn {
                            column {
                                columnPrefix("uu")
                                columnName("id")
                            }
                        }
                        addColumn {
                            column {
                                columnPrefix("uu")
                                columnName("name")
                            }
                        }
                        addColumn {
                            column {
                                columnPrefix("uu")
                                columnName("family")
                            }
                        }
                        addColumn {
                            column {
                                columnPrefix("uu")
                                columnName("age")
                            }
                        }
                        addColumn {
                            column {
                                columnPrefix("up")
                                columnName("phone")
                            }
                        }
                    }
                    table{
                        table("user_users")
                        alias("uu")
                    }
                    joins {
                        addJoin {
                            innerJoin()
                            table {
                                table("user_phones")
                                alias("up")
                            }
                            condition {
                                logicalOn()
                                addCondition {
                                    sideLeft {
                                        columnPrefix("uu")
                                        columnName("id")
                                    }
                                    operationEqual()
                                    sideRight {
                                        columnPrefix("up")
                                        columnName("user_id")
                                    }
                                }
                            }
                        }
                    }
                    where{
                        conditions {

                            addGroup {
                                addCondition {
                                    sideLeft {
                                        columnPrefix("uu")
                                        columnName("id")
                                    }
                                    operationIsNotNull();
                                }
                            }

                            addCondition {
                                logicalAnd()
                                sideLeft {
                                    columnPrefix("uu")
                                    columnName("id")
                                }
                                operationEqual()
                                sideRightValue(  "id1" ,1)
                            }

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
        .limit {
            setOptionLimit(2)
        }
        .offset {
            setOptionOffset(0)
        }
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
            orderAsc()
            addColumn{
                columnPrefix("u")
                columnName("id")
            }
        }




    /*var q2 = QueryToolsTable()
        .table("users")
        .alias("u")

    println("aaa: " +q2.toSql(sqlDialect))*/


    println(query.toSqlReadable(sqlDialect))


    val queryBuilder =
        QueryContext(dbBuilder.getDialect())
            .setQuery(query)
            .execute();






    /*val queryCreated : String? = query.toSql(sqlDialect);
    dbBuilder.fetch(queryCreated){
            result->

        if (result != null){

            var number = 1;
            while (result.next()) {

                val id =     result.getInt("id")
                val name =   result.getString("name")
                val family = result.getString("family")
                val age =    result.getString("age")
                val phone =  result.getString("phone")

                println("User: $number - $id $name $family $age $phone");
                number ++;
            }
        }
    }*/


    /*val query2 = "select id , name from user_users where id= ?";
    val ps = db.prepareStatement(query2)
    //ps.setInt(1 , 1);
    ps.setObject(1 , 1);
    val exe = ps.executeQuery();

    while (exe.next()){
        val id =     exe.getInt("id")
        val name =   exe.getString("name")
        println("User: - $id $name ");
    }*/

}