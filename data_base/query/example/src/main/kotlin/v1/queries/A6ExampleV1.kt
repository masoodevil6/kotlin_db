package gog.my_project.data_base.query.example.v1.queries


import gog.my_project.data_base.query.definition.interfaces.IQuery
import gog.my_project.data_base.query.renderer.manager.QueryManager
import gog.my_project.data_base.query.renderer.tools.ExecuteResult
import gog.my_project.tools.scripts.StringTools

class A6ExampleV1(): IAExampleV1 {

    override fun query(): IQuery {
        /*return QueryBuilder()
            .withs{
                addWith {
                    with(CteInfoUser(1))
                }
            }
            .select{
                addColumn{
                    column {
                        column(CteInfoUser::class , CteInfoUser::cteUserId)
                    }
                }
                addColumn{
                    column {
                        column(CteInfoUser::class , CteInfoUser::cteUserName)
                    }
                }
                addColumn{
                    column {
                        column(CteInfoUser::class , CteInfoUser::cteUserFamily)
                    }
                }
                addColumn{
                    column {
                        column(CteInfoUser::class , CteInfoUser::cteUserAge)
                    }
                }
                addColumn{
                    column {
                        column(CteInfoUser::class , CteInfoUser::userPhone)
                    }
                }
            }
            .table{
                table(CteInfoUser::class )
            }
            .limit {
                setOptionLimit(2)
            }
            .offset {
                setOptionOffset(0)
            }
            .group {
                addColumn{
                    column(CteInfoUser::class , CteInfoUser::cteUserId)
                }
                addColumn{
                    column(CteInfoUser::class , CteInfoUser::cteUserName)
                }
                addColumn{
                    column(CteInfoUser::class , CteInfoUser::cteUserFamily)
                }
                addColumn{
                    column(CteInfoUser::class , CteInfoUser::cteUserAge)
                }
                addColumn{
                    column(CteInfoUser::class , CteInfoUser::userPhone)
                }
            }
            .order {
                orderAsc()
                addColumn{
                    column(CteInfoUser::class , CteInfoUser::cteUserId)
                }
            }*/
        return QueryBuilder();
    }

    override fun execute(queryManager: QueryManager) {
        queryManager.execute(
            queryBuilder = this.query() as IQueryBuilder,
            blockQueryInfo = {
                    query , paramsMap->

                println("\n=============================================");
                println("V1-Ex6: Query with cte ");
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
                                val phone =    rs.getInt("phone")
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