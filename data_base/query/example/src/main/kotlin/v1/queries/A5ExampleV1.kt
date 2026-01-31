package gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.query.definition.interfaces.IQueryDefinition
import gog.my_project.data_base.query.renderer.manager.QueryManager
import gog.my_project.data_base.query.renderer.tools.ExecuteResult
import gog.my_project.tools.scripts.StringTools


class A5ExampleV1()
    : IAExampleV1 {


    override fun query(): IQueryDefinition {
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
                            column(Users::class ,Users::userAge)
                        }
                        operationIs()
                        sideValue(
                            "user_age" ,
                            1
                        )
                    }
                }
            }
    }

    override fun execute(queryManager: QueryManager) {
        queryManager.execute(
            queryBuilder = this.query() as IQueryBuilder,
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