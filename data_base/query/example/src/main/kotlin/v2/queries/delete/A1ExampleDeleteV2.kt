package gog.my_project.data_base.query.example.v2.queries.delete

import gog.my_project.data_base.query.example.v2.queries.models.UserModelV2
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.example.v2.queries.IExampleV2
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A1ExampleDeleteV2 : IExampleV2<IQueryRenderDeleteApi> {
    override fun query(): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        //.addTarget("uu")
        .table {
            table(UserModelV2::class)
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        //tableSelector("uu")
                        //columnSelector(UserModelV2::class, UserModelV2::id)
                        tableSelector(UserModelV2::class)
                        columnSelector(UserModelV2::id)
                    }
                    operationEqual()
                    sideValueParam {
                        paramName("id1")
                        paramValue(5)
                    }
                }
            }
        }

    override fun execute(queryManager: IQueryBuilderExecutor) {

        /*val affectedRows = queryManager
            .queryBuilder { operation ->
                operation.queryDelete {
                    QueryRenderDeleteBuilder()
                        //.addTarget("uu")
                        .table {
                            table(UserModelV2::class)
                        }
                        .where {
                            conditions {
                                addCondition {
                                    logicalAnd()
                                    sideSelector {
                                        //tableSelector("uu")
                                        //columnSelector(UserModelV2::class, UserModelV2::id)
                                        tableSelector(UserModelV2::class)
                                        columnSelector(UserModelV2::id)
                                    }
                                    operationEqual()
                                    sideValueParam {
                                        paramName("id1")
                                        paramValue(5)
                                    }
                                }
                            }
                        }
                        .where {
                            conditions {
                                addCondition {
                                    logicalAnd()
                                    sideSelector {
                                        tableSelector(UserModelV2::class)
                                        columnSelector(UserModelV2::id)
                                    }
                                    operationEqual()
                                    sideValueParam {
                                        paramName("id1")
                                        paramValue(5)
                                    }
                                }
                            }
                        }
                }
            }
            .sql { builtQuery -> println("\n=============================================\nV2-Delete query: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { affectedRows ->
                println("V2-Delete: $affectedRows row(s) deleted")
                affectedRows
            }
            .error { failure -> println("V2-Delete error: $failure") }
            .toValue()*/

        queryManager
            .queryBuilder(query())
            .sql { builtQuery -> println("\n=============================================\nV2-Delete query: ${builtQuery.query}; params: ${builtQuery.params}") }
            .execute()
            .success { affectedRows ->
                println("V2-Delete: $affectedRows row(s) deleted")
                affectedRows
            }
            .error { failure -> println("V2-Delete error: $failure") }
    }
}
