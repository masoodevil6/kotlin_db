package gog.my_project.data_base.query_builder.examples.v1.queries

import gog.my_project.data_base.models.users.UserPhones
import gog.my_project.data_base.models.users.Users
import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager
import gog.my_project.data_base.query_builder.renderer.tools.ExecuteResult
import gog.my_project.tools.scripts.StringTools

class A2ExampleV1 : IAExampleV1 {


    override fun query(): IQueryBuilder {
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
            }
            .table{
                table(Users::class)
            }
            .where{
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            column(Users::class ,Users::userId)
                        }
                        operationIn()
                        sideValueCollection(
                            "userId",
                            {
                                addParam(1)
                                //addParamIn(2)
                            },
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
                println("V1-Ex2: select with whereIn");
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
                                println("exe: - $id $name $family $age ");
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