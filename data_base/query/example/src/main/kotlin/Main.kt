package gog.my_project.data_base.query.example

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.query.example.managers.IManagerExample
import gog.my_project.data_base.query.example.managers.ManagerExampleV1
import gog.my_project.data_base.query.example.managers.ManagerExampleV2
import gog.my_project.data_base.query.example.managers.ManagerExampleV3
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


    val queryManager = QueryBuilderExecutor();

    val selectedVersion = args.firstOrNull()?.takeIf { it.isNotBlank() }?.lowercase() ?: "v3"
    val statusRunSelect = configuredRunOption("query.example.run.select", true)
    val statusRunInsert = configuredRunOption("query.example.run.insert", false)
    val statusRunUpdate = configuredRunOption("query.example.run.update", false)
    val statusRunDelete = configuredRunOption("query.example.run.delete", false)
    val manager: IManagerExample<*, *, *, *> = when (selectedVersion) {
        "v1" -> ManagerExampleV1(
            statusRunSelect = statusRunSelect,
            statusRunInsert = statusRunInsert,
            statusRunUpdate = statusRunUpdate,
            statusRunDelete = statusRunDelete,
        )
        "v2" -> ManagerExampleV2(
            statusRunSelect = statusRunSelect,
            statusRunInsert = statusRunInsert,
            statusRunUpdate = statusRunUpdate,
            statusRunDelete = statusRunDelete,
        )
        "v3" -> ManagerExampleV3(
            statusRunSelect = statusRunSelect,
            statusRunInsert = statusRunInsert,
            statusRunUpdate = statusRunUpdate,
            statusRunDelete = statusRunDelete,
        )
        else -> throw IllegalArgumentException("Unsupported example version '$selectedVersion'. Choose v1, v2, or v3.")
    }
    manager.readyListExamples()
    manager.renderExamples(queryManager)

}

private fun configuredRunOption(name: String, default: Boolean): Boolean {
    val value = System.getProperty(name) ?: return default
    return value.toBooleanStrictOrNull()
        ?: throw IllegalArgumentException("System property '$name' must be 'true' or 'false'")
}
