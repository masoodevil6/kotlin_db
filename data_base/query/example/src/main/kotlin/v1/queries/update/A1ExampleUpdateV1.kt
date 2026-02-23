package gog.my_project.data_base.query.example.v1.queries.update

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A1ExampleUpdateV1: IExampleV1<IQueryRenderUpdateApi> {

    override fun query(): IQueryRenderUpdateApi {
        return QueryRenderUpdateBuilder()
            .table{
                table("user_users" , "uu")
            }
            .addValue {
                column("uu" ,"name" , "--changed--")
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
                println("V1-Ex1: Update Sample ");
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
                        println("exe:  ${result.result} row Updated ");
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