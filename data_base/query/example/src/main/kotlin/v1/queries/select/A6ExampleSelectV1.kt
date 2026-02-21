package gog.my_project.data_base.query.example.v1.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.cte.modules.users.CteInfoUser
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class A6ExampleSelectV1(): IExampleV1 {

    override fun query(): IQueryRenderSelectApi {
        return QueryRenderSelectBuilder()
            .withs{
                addWith {
                    with("cte_info_user" , CteInfoUser(1))
                }
            }
            .select{
                addColumn{
                    column {
                        cteColumn("ciu" , "cte_user_id")
                    }
                }
                addColumn{
                    column {
                        cteColumn("ciu" , "cte_user_name")
                    }
                }
                addColumn{
                    column {
                        cteColumn("ciu" , "cte_user_family")
                    }
                }
                addColumn{
                    column {
                        cteColumn("ciu" , "cte_user_age")
                    }
                }
                addColumn{
                    column {
                        cteColumn("ciu" , "cte_user_phone")
                    }
                }
            }
            .table{
                cte("cte_info_user" , "ciu")
            }
            .limit {
                setOptionLimit(2)
            }
            .offset {
                setOptionOffset(0)
            }
    }

    override fun execute(queryManager : IQueryBuilderExecutor) {
        queryManager.execute(
            queryBuilder = this.query() ,
            blockQueryInfo = {
                    query , paramsMap->

                println("\n=============================================");
                println("V1-Ex6: Query with cte ");
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
                                val id =       rs.getInt("cte_user_id")
                                val name =     rs.getString("cte_user_name")
                                val family =   rs.getString("cte_user_family")
                                val age =      rs.getInt("cte_user_age")
                                val phone =    rs.getString("cte_user_phone")
                                println("exe: - $id $name $family $age $phone ");
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