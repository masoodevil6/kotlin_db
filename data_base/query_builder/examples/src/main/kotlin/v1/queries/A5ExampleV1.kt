package gog.my_project.data_base.query_builder.examples.v1.queries

import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager
import gog.my_project.tools.scripts.StringTools

class A5ExampleV1()
    : IAExampleV1 {


    override fun query(): IQueryBuilder {
        return QueryBuilder()
            .select {
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
            }
            .table {
                table("user_users")
                alias("uu")
            }
            .where{
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            columnPrefix("u")
                            columnName("age")
                        }
                        operationIs()
                        sideValue(
                            "user_age" ,
                            null
                        )
                    }
                }
            }
    }

    override fun execute(queryManager: QueryManager) {
        queryManager.execute(
            queryBuilder = this.query() ,
            blockQueryInfo = {
                    query , paramsMap->

                println("\n=============================================");
                println("V1-Ex5: Create error");
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
                    status , message , result->
                if (status){
                    println("msg: - $message");
                    while (result!!.next()){
                        val id =       result.getInt("id")
                        val name =     result.getString("name")
                        val family =   result.getString("family")
                        val age =      result.getInt("age")
                        println("exe: - $id $name $family $age ");
                    }
                }
                else{
                    println("error: - $message");
                }
            }
        )
    }


}