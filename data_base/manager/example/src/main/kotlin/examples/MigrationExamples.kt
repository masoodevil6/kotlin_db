package gog.my_project.data_base_manager.example.examples

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandle

/** Runs only the migrations assigned to this database's catalog registration. */
fun migrateMainDatabase(database: DatabaseHandle): ExecuteResult<Unit> {
    var result: ExecuteResult<Unit>? = null
    database.migrate { completed -> result = completed }
    return result ?: ExecuteResult.Failure(
        IllegalStateException("Migration execution did not deliver a synchronous result"),
    )
}
