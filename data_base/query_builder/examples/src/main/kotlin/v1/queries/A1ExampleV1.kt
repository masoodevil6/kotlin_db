package  gog.my_project.data_base.query_builder.examples.v1.queries

import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager


class A1ExampleV1()
    : IAExampleV1 {


    override fun query(): IQueryBuilder {
        return QueryBuilder()
            .withs{
                addWith {
                    withName("users")
                    withBody {
                        select {
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
                            addColumn {
                                column {
                                    columnPrefix("up")
                                    columnName("phone")
                                }
                            }
                        }
                        table{
                            table("user_users")
                            alias("uu")
                        }
                        joins {
                            addJoin {
                                innerJoin()
                                table {
                                    table("user_phones")
                                    alias("up")
                                }
                                condition {
                                    logicalOn()
                                    addCondition {
                                        sideLeft {
                                            columnPrefix("uu")
                                            columnName("id")
                                        }
                                        operationEqual()
                                        sideRight {
                                            columnPrefix("up")
                                            columnName("user_id")
                                        }
                                    }
                                }
                            }
                        }
                        where{
                            conditions {

                                addGroup {
                                    addCondition {
                                        sideLeft {
                                            columnPrefix("uu")
                                            columnName("id")
                                        }
                                        operationIsNotNull();
                                    }
                                }

                                addCondition {
                                    logicalAnd()
                                    sideLeft {
                                        columnPrefix("uu")
                                        columnName("id")
                                    }
                                    operationEqual()
                                    sideRightValue(  "id1" ,1)
                                }

                            }
                        }
                    }
                }
            }
            .select{
                addColumn{
                    column {
                        columnPrefix("u")
                        columnName("id")
                    }
                }
                addColumn{
                    column {
                        columnPrefix("u")
                        columnName("name")
                    }
                }
                addColumn{
                    column {
                        columnPrefix("u")
                        columnName("family")
                    }
                }
                addColumn{
                    column {
                        columnPrefix("u")
                        columnName("age")
                    }
                }
                addColumn{
                    column {
                        columnPrefix("u")
                        columnName("phone")
                    }
                }
            }
            .table{
                table("users")
                alias("u")
            }
            .limit {
                setOptionLimit(2)
            }
            .offset {
                setOptionOffset(0)
            }
            .group {
                addColumn{
                    columnPrefix("u")
                    columnName("id")
                }
                addColumn{
                    columnPrefix("u")
                    columnName("name")
                }
                addColumn{
                    columnPrefix("u")
                    columnName("family")
                }
                addColumn{
                    columnPrefix("u")
                    columnName("age")
                }
                addColumn{
                    columnPrefix("u")
                    columnName("phone")
                }
            }
            .order {
                orderAsc()
                addColumn{
                    columnPrefix("u")
                    columnName("id")
                }
            }
    }

    override fun execute(queryManager : QueryManager) {
        queryManager.execute(
            this.query() ,
            blockExecute = {
                    result->
                while (result!!.next()){
                    val id =       result.getInt("id")
                    val name =     result.getString("name")
                    val family =   result.getString("family")
                    val age =      result.getInt("age")
                    val phone =    result.getString("phone")
                    println("User: - $id $name $family $age $phone");
                }
            }
        )
    }


}