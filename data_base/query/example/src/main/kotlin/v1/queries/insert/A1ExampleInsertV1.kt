package gog.my_project.data_base.query.example.v1.queries.insert

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A1ExampleInsertV1()
    : IExampleV1<IQueryRenderInsertApi> {
    override fun query(): IQueryRenderInsertApi {
        return QueryRenderInsertBuilder()
            .table("user_users")
            .addColumn {
                column("name" , "Ali")
            }
            .addColumn {
                column("family" , "Sadegi")
            }
            .addColumn {
                column("age" , 50)
            }
    }

    override fun execute(queryManager: IQueryBuilderExecutor) {
        queryManager.execute(
            queryBuilder = this.query() ,
            blockQueryInfo = {
                    query , paramsMap->

                println("\n=============================================");
                println("V1-Ex1: Insert Sample ");
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
                    is ExecuteResult.Success -> {
                        result.result?.let {
                                rs->

                            println("exe:  $rs ");

                        }
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