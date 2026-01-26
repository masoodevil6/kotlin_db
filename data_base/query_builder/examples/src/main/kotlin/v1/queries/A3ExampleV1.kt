package gog.my_project.data_base.query_builder.examples.v1.queries

import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager
import gog.my_project.data_base.query_builder.renderer.tools.ExecuteResult
import gog.my_project.tools.scripts.StringTools

class A3ExampleV1: IAExampleV1 {

    override fun query(): IQueryBuilder {
        return QueryBuilder()
            .select {
                addColumn {
                    column {
                        columnPrefix("uu")
                        columnName("id")
                    }
                }
                addColumn {
                    column {
                        columnPrefix("uu")
                        columnName("name")
                    }
                }
                addColumn {
                    column {
                        columnPrefix("uu")
                        columnName("family")
                    }
                }
                addColumn {
                    column {
                        columnPrefix("uu")
                        columnName("age")
                    }
                }
            }
            .table {
                table("user_users")
                alias("uu")
            }
            .where{
                conditions {
                    addGroup {
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                columnPrefix("uu")
                                columnName("name")
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
                                columnPrefix("uu")
                                columnName("name")
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
                                columnPrefix("uu")
                                columnName("name")
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
                                columnPrefix("uu")
                                columnName("name")
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

    override fun execute(queryManager: QueryManager) {
        queryManager.execute(
            queryBuilder = this.query() ,
            blockQueryInfo = {
                    query , paramsMap->
                println("\n=============================================");
                println("V1-Ex3: select with WhereLike");
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