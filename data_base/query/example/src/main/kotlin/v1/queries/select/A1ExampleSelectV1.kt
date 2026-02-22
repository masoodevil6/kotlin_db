package gog.my_project.data_base.query.example.v1.queries.select

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import java.sql.ResultSet

class A1ExampleSelectV1()
    : IExampleV1<IQueryRenderSelectApi> {


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
                        tableColumn("uu" , "name")
                    }
                    alias("user_name")
                }
                addColumn {
                    column {
                        tableColumn("uu" , "family")
                    }
                    alias("user_family")
                }
                addColumn {
                    column {
                        tableColumn("uu" , "age")
                    }
                    alias("user_age")
                }
                addColumn {
                    column {
                        tableColumn("up" , "phone")
                    }
                    alias("user_phone")
                }
            }
            .table{
                table("user_users" , "uu")
            }
            .joins {
                addJoin {
                    innerJoin()
                    table {
                        table("user_phones" , "up")
                    }
                    condition {
                        logicalOn()
                        addCondition {
                            sideSelector {
                                tableColumn("uu" , "id")
                            }
                            operationEqual()
                            sideValue {
                                tableColumn("up" , "user_id")
                            }
                        }
                    }
                }
            }
            .where{
                conditions {

                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableColumn("uu" , "id")
                        }
                        operationEqual()
                        sideValue(
                            "id1" ,
                            1
                        )
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
                println("V1-Ex1: select simple");
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
                                val name =     rs.getString("user_name")
                                val family =   rs.getString("user_family")
                                val age =      rs.getInt("user_age")
                                val phone =    rs.getString("user_phone")
                                println("exe: - $id $name $family $age $phone");
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