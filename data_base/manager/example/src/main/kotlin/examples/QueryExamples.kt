package gog.my_project.data_base_manager.example.examples

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.executor.result.QueryRow

/** Runs a SELECT through the handle's shared, non-transactional Connection Scope. */
fun readMainCustomers(database: DatabaseHandleApi): ExecuteResult<List<QueryRow>> =
    database.connection { executor ->
        executor.queryBuilderSelect { query ->
            query.select {
                addColumn {
                    column { columnSelector("id") }
                    alias("customer_id")
                    execute(DataType.INT)
                }
                addColumn {
                    column { columnSelector("display_name") }
                    alias("display_name")
                    execute(DataType.STRING)
                }
            }
            query.from { table("manager_example_customers") }
        }.execute()
    }

/** Runs the same SELECT inside an Operation-owned Transaction Scope. */
fun readMainCustomersInTransaction(database: DatabaseHandleApi): ExecuteResult<List<QueryRow>> =
    database.transaction { transaction ->
        transaction.queryBuilderSelect { query ->
            query.select {
                addColumn {
                    column { columnSelector("id") }
                    alias("customer_id")
                    execute(DataType.INT)
                }
                addColumn {
                    column { columnSelector("display_name") }
                    alias("display_name")
                    execute(DataType.STRING)
                }
            }
            query.from { table("manager_example_customers") }
        }.execute()
    }
