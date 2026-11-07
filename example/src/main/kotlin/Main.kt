package gog.my_project.example

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.executor.result.QueryRow

private const val CUSTOMER_TABLE = "manager_customers"

fun main() {
    if (!System.getProperty("database.example.run", "false").toBooleanStrict()) {
        println("Database calls are disabled. Set -Pdatabase.example.run=true to opt in.")
        return
    }

    // Reuse these exact instances for both catalog registration and handle lookup.
    val mainDatabase = MainDatabase
    val analyticsDatabase = AnalyticsDatabase("eu-west")

    val manager: DatabaseManagerApi = DatabaseManager {
        migrationTags = DataBaseMigrationTags
        database(mainDatabase) {
            config = config("MAIN", "main_example")
        }
        database(analyticsDatabase) {
            config = config("ANALYTICS", "analytics_example")
        }
    }

    try {
        val main: DatabaseHandleApi = manager[mainDatabase]
        val analytics: DatabaseHandleApi = manager[analyticsDatabase]

        println("Registered keys: ${manager.registeredKeys().joinToString()}")
        println("Migration execution is temporarily unavailable; the example tables must already exist.")

        reportCustomers("Main query", main.connection { executor -> selectCustomers(executor) })
        reportCustomers(
            "Main transaction (read-only SELECT)",
            main.transaction { transaction -> selectCustomers(transaction) },
        )
    } finally {
        manager.shutdown()
    }
}

private fun config(
    environmentPrefix: String,
    defaultDatabaseName: String,
): DatabaseConfig {
    val name = "${environmentPrefix}_DB_NAME"
    val databaseName = System.getenv(name)?.takeIf(String::isNotBlank) ?: defaultDatabaseName
    val user = requiredEnvironment("${environmentPrefix}_DB_USER")
    val password = requiredEnvironment("${environmentPrefix}_DB_PASSWORD", allowWhitespace = true)
    val portVariable = "${environmentPrefix}_DB_PORT"
    val port = System.getenv(portVariable)?.let { rawPort ->
        val parsedPort = rawPort.toIntOrNull()
            ?: throw IllegalArgumentException("$portVariable must be an integer")
        require(parsedPort in 1..65535) { "$portVariable must be between 1 and 65535" }
        parsedPort
    } ?: 3306

    val domainVariable = "${environmentPrefix}_DB_DOMAIN"
    val domain = System.getenv(domainVariable)?.also {
        require(it.isNotBlank()) { "$domainVariable must not be blank" }
    } ?: "jdbc:mysql://127.0.0.1"

    return DatabaseConfig(
        connection = DatabaseConnectionConfig(
            dbDomain = domain,
            dbPort = port,
            dbName = databaseName,
            dbUserName = user,
            dbPassword = password,
        ),
        platform = DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
    )
}

private fun requiredEnvironment(name: String, allowWhitespace: Boolean = false): String {
    val value = System.getenv(name)
    require(if (allowWhitespace) !value.isNullOrEmpty() else !value.isNullOrBlank()) {
        "Set $name before running this example"
    }
    return requireNotNull(value)
}



private fun selectCustomers(executor: IQueryBuilderExecutor): ExecuteResult<List<QueryRow>> =
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
        query.from { table(CUSTOMER_TABLE) }
    }.execute()

private fun reportCustomers(label: String, result: ExecuteResult<List<QueryRow>>) {
    when (result) {
        is ExecuteResult.Success -> {
            val rows = result.result
            if (rows == null) {
                println("$label succeeded without a result value")
            } else if (rows.isEmpty()) {
                println("$label succeeded; no customers were returned")
            } else {
                rows.forEach { row ->
                    println("$label: id=${row.getValue("customer_id")}, display_name=${row.getValue("display_name")}")
                }
            }
        }
        is ExecuteResult.Failure -> reportFailure(label, result.exception)
    }
}

private fun reportFailure(label: String, failure: Throwable) {
    val credentials = listOf(
        "MAIN_DB_USER",
        "MAIN_DB_PASSWORD",
        "ANALYTICS_DB_USER",
        "ANALYTICS_DB_PASSWORD",
    )
        .mapNotNull(System::getenv)
        .filter(String::isNotEmpty)
    val details = generateSequence(failure) { it.cause }
        .take(8)
        .joinToString(" caused by ") { cause ->
            var message = cause.message.orEmpty()
            credentials.forEach { secret -> message = message.replace(secret, "<redacted>") }
            "${cause::class.qualifiedName}: $message"
        }
    System.err.println("$label failed: $details")
}
