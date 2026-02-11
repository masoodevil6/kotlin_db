package gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.models.modules.users.Users
import gog.my_project.data_base.query.api.interfaces.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor


class A3ExampleV1: IAExampleV1 {

    override fun query(): IQueryRenderSelectApi {
        return QueryRenderSelectBuilder()
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
                    addGroup {
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                column(Users::class ,Users::userName)
                            }
                            operationLike()
                            sideValue(
                                "user_name1" ,
                                "Meh"
                            )
                        }
                        addCondition {
                            logicalOr()
                            sideSelector {
                                column(Users::class ,Users::userName)
                            }
                            operationLike()
                            sideValue(
                                "user_name2" ,
                                "Meh%"
                            )
                        }
                        addCondition {
                            logicalOr()
                            sideSelector {
                                column(Users::class ,Users::userName)
                            }
                            operationLike()
                            sideValue(
                                "user_name3" ,
                                "%Meh"
                            )
                        }
                        addCondition {
                            logicalOr()
                            sideSelector {
                                column(Users::class ,Users::userName)
                            }
                            operationLike()
                            sideValue(
                                "user_name4" ,
                                "%Meh%"
                            )
                        }
                    }
                }
            }
    }

    override fun execute(queryManager : IQueryBuilderExecutor) {
        queryManager.execute(
            queryBuilder = this.query() ,
            blockQueryInfo = {
                    query , paramsMap->
                println("\n=============================================");
                println("V1-Ex3: select with WhereLike");
                println("---------------------------");

                print("query: ${query} \n");
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