package gog.my_project.data_base.query.example.v1.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A7ExampleSelectV1(): IExampleV1<IQueryRenderSelectApi> {

    override fun query(): IQueryRenderSelectApi {
        return QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column {
                        tableColumn("uu" , "id")
                    }
                    alias("user_id")
                }
                addColumn {
                    column {
                        tableAttribute("concat( uu.name , '-' , uu.family )")
                    }
                    alias("user_full_name")
                }
                addColumn {
                    column {
                        tableColumn("uu" , "age")
                    }
                    alias("user_age")
                }
            }
            .table{
                table("user_users" , "uu")
            }
    }

    override fun execute(queryManager : IQueryBuilderExecutor) {
        queryManager.execute(
            queryBuilder = this.query() ,
            blockQueryInfo = {
                    query , paramsMap->

                println("\n=============================================");
                println("V1-Ex7: Query table attribute ");
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
                    is ExecuteResult.Success -> {
                        result.result?.let {
                                rs->
                            while (rs!!.next()){
                                val id =       rs.getInt("user_id")
                                val fullName =     rs.getString("user_full_name")
                                val age =      rs.getInt("user_age")
                                println("exe: - $id $fullName $age ");
                            }
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