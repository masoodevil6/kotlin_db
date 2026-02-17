package gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A7ExampleV1(): IAExampleV1 {

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
                    is ExecuteResult.SuccessExecute -> {
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