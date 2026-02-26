package gog.my_project.data_base.query.example.v1.queries.delete

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A1ExampleDeleteV1 : IExampleV1<IQueryRenderDeleteApi> {

    override fun query(): IQueryRenderDeleteApi {
        return QueryRenderDeleteBuilder()
            .addTarget("uu")
            .table {
                table("user_users" , "uu")
            }
            .where {
                conditions {

                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableColumn("uu" , "id")
                        }
                        operationEqual()
                        sideValue(
                            "id1" ,
                            5
                        )
                    }

                }
            }
    }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager.execute(
            queryBuilder = this.query() ,
            blockQueryInfo = {
                    query , paramsMap->

                println("\n=============================================");
                println("V1-Ex1: delete Sample ");
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
            blockExecute = { result ->
                when(result) {
                    is ExecuteResult.Success<*> -> {
                        println("exe:  ${result.result} row delete ");
                    }
                    is ExecuteResult.Failure -> {
                        println("error: - ${result.exception.toString()}");
                    }
                    else->{

                    }
                }
            }
        )
    }

}