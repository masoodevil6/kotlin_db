package  gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.query.definition.interfaces.IQuery
import gog.my_project.data_base.query.renderer.manager.QueryManager
import gog.my_project.data_base.query.renderer.tools.ExecuteResult
import gog.my_project.tools.scripts.StringTools

class A1ExampleV1()
    : IAExampleV1 {


    /*override fun query(): IQueryBuilder {
        return QueryBuilder()
            .withs{
                addWith {
                    withName("users")
                    withBody {
                        select {
                            addColumn {
                                column {
                                    column(Users::userId)
                                }
                            }
                            addColumn {
                                column {
                                    column(Users::userName)
                                }
                            }
                            addColumn {
                                column {
                                    column(Users::userFamily)
                                }
                            }
                            addColumn {
                                column {
                                    column(Users::userAge)
                                }
                            }
                            addColumn {
                                column {
                                    column(UserPhones::userPhone)
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
                                        sideSelector {
                                            column(Users::userId)
                                        }
                                        operationEqual()
                                        sideValue {
                                            column(UserPhones::userId)
                                        }
                                    }
                                }
                            }
                        }
                        where{
                            conditions {

                                addCondition {
                                    logicalAnd()
                                    sideSelector {
                                        column(Users::userId)
                                    }
                                    operationEqual()
                                    sideValue(
                                        "id1" ,
                                        1
                                    )
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
    }*/

    override fun query(): IQuery {
        return QueryBuilder()
            .select {
                addColumn {
                    column {
                        column(Users::class ,Users::userId)
                    }
                }
                addColumn {
                    column {
                        column(Users::class ,Users::userName)
                    }
                }
                addColumn {
                    column {
                        column(Users::class ,Users::userFamily)
                    }
                }
                addColumn {
                    column {
                        column(Users::class ,Users::userAge)
                    }
                }
                addColumn {
                    column {
                        column(UserPhones::class ,UserPhones::userPhone)
                    }
                }
            }
            .table{
                table(Users::class)
            }
            .joins {
                addJoin {
                    innerJoin()
                    table {
                        table(UserPhones::class)
                    }
                    condition {
                        logicalOn()
                        addCondition {
                            sideSelector {
                                column(Users::class ,Users::userId)
                            }
                            operationEqual()
                            sideValue {
                                column(UserPhones::class ,UserPhones::userId)
                            }
                        }
                    }
                }
            }
            .where{
                conditions {

                    addCondition {
                        logicalAnd()
                        sideSelector {
                            column(Users::class ,Users::userId)
                        }
                        operationEqual()
                        sideValue(
                            "id1" ,
                            1
                        )
                    }

                }
        }
    }

    override fun execute(queryManager : QueryManager) {
        queryManager.execute(
            queryBuilder = this.query() as IQueryBuilder,
            blockQueryInfo = {
                query , paramsMap->
                println("\n=============================================");
                println("V1-Ex1: select simple");
                println("---------------------------");

                print("query: ${StringTools.formatSql(query)} \n");
                println("---------------------------");
                var paramsStr = "";
                paramsMap.forEach {
                    paramsStr += "\n ${it.key} = ${it.value} ";
                }
                print("params: $paramsStr \n");
                println("---------------------------");
            },
            blockExecute = {
                result ->
                when(result) {
                    is ExecuteResult.SuccessExecute -> {
                        result.result?.let {
                            rs->
                            while (rs!!.next()){
                                val id =       rs.getInt("id")
                                val name =     rs.getString("name")
                                val family =   rs.getString("family")
                                val age =      rs.getInt("age")
                                val phone =    rs.getString("phone")
                                println("exe: - $id $name $family $age $phone");
                            }
                        }
                    }
                    is ExecuteResult.ErrorExecute -> {
                        println("error: - ${result.exception.toString()}");
                    }
                    is ExecuteResult.Error -> {
                        println("error: - ${result.error}");
                    }
                    else->{

                    }
                }
            }
        )
    }


}