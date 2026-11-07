package gog.my_project.data_base_query.example

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePoolConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.example.runners.ExampleRunOptions
import gog.my_project.data_base_query.example.runners.ExampleVersionRunner
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {


    val databaseContext = DatabaseContext(
        name = "query-example",
        config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://127.0.0.1",
                dbPort = 3306,
                dbName = "kotlin_db",
                dbUserName = "root",
                dbPassword = "",
            ),
            pool = DatabasePoolConfig(
                maxPoolSize = 10
            ),
            platform = DatabasePlatformConfig(
                dialect = DialectQuery.MY_SQL
            ),
        ),
    )


    val queryManager = QueryBuilderExecutor(databaseContext)
    val selectedVersion = args.firstOrNull()?.takeIf { it.isNotBlank() }?.lowercase() ?: "v3"
    val options = ExampleRunOptions(
        runSelect = configuredRunOption("query.example.run.select", true),
        runInsert = configuredRunOption("query.example.run.insert", false),
        runUpdate = configuredRunOption("query.example.run.update", false),
        runDelete = configuredRunOption("query.example.run.delete", false),
        runTransaction = configuredRunOption("query.example.run.transaction", false),
    )

    when (val result = ExampleVersionRunner.run(selectedVersion, queryManager, options)) {
        is ExecuteResult.Success -> Unit
        is ExecuteResult.Failure -> throw result.exception
    }

}

private fun configuredRunOption(name: String, default: Boolean): Boolean {
    val value = System.getProperty(name) ?: return default
    return value.toBooleanStrictOrNull()
        ?: throw IllegalArgumentException("System property '$name' must be 'true' or 'false'")
}
