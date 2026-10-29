package gog.my_project.data_base.query.example

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.example.runners.ExampleRunOptions
import gog.my_project.data_base.query.example.runners.ExampleVersionRunner
import gog.my_project.data_base.query.executer.manager.QueryBuilderExecutor

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {


    DefaultDatabaseConfig.config=
        DatabaseConfig(
            dbDomain = "jdbc:mysql://127.0.0.1",
            dbPort = 3306,
            dbName = "kotlin_db",
            dbUserName = "root",
            dbPassword = "",
            dbPoolSize = 10,
            dialect = DialectQuery.MY_SQL
        );


    val queryManager = QueryBuilderExecutor()
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
