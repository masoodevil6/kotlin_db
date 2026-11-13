package gog.my_project.example

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseWatchSubscription
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_manager.facade.watch
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.events.MigrationEvent
import gog.my_project.data_base_migration.executor.controller.MigrationController
import gog.my_project.data_base_migration.facade.MigrationFacade
import gog.my_project.data_base_migration.facade.migration
import gog.my_project.data_base_migration.facade.migrationConfiguration
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.executor.result.QueryRow
import gog.my_project.example.migrations.CreateManagerCustomers
import gog.my_project.example.migrations.CreateManagerEvents
import model.migration_status.MigrationStatusReport

private const val CUSTOMER_TABLE = "manager_customers"
private const val EVENT_TABLE = "manager_events"
private const val SAMPLE_CUSTOMER = "Example Customer"
private const val SAMPLE_EVENT = "Example Event"


/*
    .\gradlew.bat :example:run --project-prop "database.example.run=true"  --project-prop "database.example.rollback=true"
*/


fun main() {

    if (!System.getProperty("database.example.run", "false").toBooleanStrict()) {
        println("Database calls are disabled. Set -Pdatabase.example.run=true to opt in.")
        return
    }
    val rollbackEnabled = System.getProperty("database.example.rollback", "false").toBooleanStrict()
    val mainConfig = databaseConfig("MAIN", "main_example")
    val analyticsConfig = databaseConfig("ANALYTICS", "analytics_example")


    val mainMigrations = mainDatabaseMigrations()
    val analyticsMigrations = analyticsDatabaseMigrations()
    val mainDatabase = MainDatabase
    val analyticsDatabase = AnalyticsDatabase("eu-west")

    val manager: DatabaseManagerApi = DatabaseManager {
        migrationTags = DataBaseMigrationTags
        database(mainDatabase) {
            config = mainConfig
            migrationConfiguration = mainMigrations
        }
        database(analyticsDatabase) {
            config = analyticsConfig
            migrationConfiguration = analyticsMigrations
        }
    }

    var failure: Throwable? = null
    var mainWatch: DatabaseWatchSubscription? = null
    var analyticsWatch: DatabaseWatchSubscription? = null
    try {
        val main = manager[mainDatabase]
        val analytics = manager[analyticsDatabase]

        mainWatch = main.watch { event -> reportDatabaseEvent("MAIN", event) }
        analyticsWatch = analytics.watch { event -> reportDatabaseEvent("ANALYTICS", event) }


        main.migration {
            init();

            requireMigrationSuccess("Main migrate", migrate())
            reportMigrationState("Main", status(), current())

            requireMigrationSuccess("Main repeated migrate", migrate())
            reportMigrationState("Main after repeated migrate", status(), current())
        }

        analytics.migration {
            init();

            requireMigrationSuccess("Analytics migrate", migrate())
            reportMigrationState("Analytics", status(), current())

            requireMigrationSuccess("Analytics repeated migrate", migrate())
            reportMigrationState("Analytics after repeated migrate", status(), current())
        }

        insertAndReadExamples(main, analytics)

        if (rollbackEnabled) {
            println("Rollback opt-in is enabled.")

            println("Starting Main rollback...")

            requireValue(
                "Main rollback block",
                main.migration {
                    requireMigrationSuccess("Main rollback", rollback())
                    reportMigrationState(
                        "Main after rollback",
                        status(),
                        current(),
                    )

                    println("Starting Main re-migration...")

                    requireMigrationSuccess("Main re-migrate", migrate())
                    reportMigrationState(
                        "Main after re-migration",
                        status(),
                        current(),
                    )
                },
            )

            println("Starting Analytics rollback...")

            requireValue(
                "Analytics rollback block",
                analytics.migration {
                    requireMigrationSuccess("Analytics rollback", rollback())
                    reportMigrationState(
                        "Analytics after rollback",
                        status(),
                        current(),
                    )

                    println("Starting Analytics re-migration...")

                    requireMigrationSuccess("Analytics re-migrate", migrate())
                    reportMigrationState(
                        "Analytics after re-migration",
                        status(),
                        current(),
                    )
                },
            )

            insertAndReadExamples(main, analytics)
        }


    } catch (error: Throwable) {
        failure = error
    } finally {
        failure = closeWatchSubscription(analyticsWatch, failure)
        failure = closeWatchSubscription(mainWatch, failure)
        try {
            manager.shutdown()
        } catch (cleanupFailure: Throwable) {
            val primary = failure
            if (primary == null) {
                failure = cleanupFailure
            } else if (cleanupFailure is Error && primary !is Error) {
                cleanupFailure.addSuppressed(primary)
                failure = cleanupFailure
            } else if (primary !== cleanupFailure) {
                primary.addSuppressed(cleanupFailure)
            }
        }
    }

    failure?.let(::reportAndFail)
    println("Database lifecycle example completed successfully.")
}

private fun reportDatabaseEvent(label: String, event: DatabaseEvent) {
    val details = when (event) {
        is OperationDatabaseEvent -> buildString {
            append("${event.type}")
            event.sql?.let { append(" sql=$it") }
            event.failure?.let { append(" failure=${it.typeName} category=${it.category}") }
        }
        is MigrationEvent -> buildString {
            append("${event.type} command=${event.command}")
            event.versionName?.let { append(" version=$it") }
            event.targetVersionName?.let { append(" target=$it") }
            event.failure?.let { append(" failure=${it.typeName} category=${it.category}") }
        }
        else -> event::class.simpleName ?: "DatabaseEvent"
    }
    println("[$label] $details")
}

private fun closeWatchSubscription(
    subscription: DatabaseWatchSubscription?,
    primaryFailure: Throwable?,
): Throwable? {
    if (subscription == null) return primaryFailure
    return try {
        subscription.close()
        primaryFailure
    } catch (cleanupFailure: Throwable) {
        if (primaryFailure == null) {
            cleanupFailure
        } else {
            if (primaryFailure !== cleanupFailure) {
                try {
                    primaryFailure.addSuppressed(cleanupFailure)
                } catch (_: Throwable) {
                    // Preserve the primary failure even if cleanup diagnostics cannot be attached.
                }
            }
            primaryFailure
        }
    }
}

private fun mainDatabaseMigrations(): MigrationConfiguration = migrationConfig {
    migrationVersion(DataBaseMigrationTags.Versions.Init) {
        migration<CreateManagerCustomers>()
    }
}

private fun analyticsDatabaseMigrations(): MigrationConfiguration = migrationConfig {
    migrationVersion(DataBaseMigrationTags.Versions.Init) {
        migrationGroup(DataBaseMigrationTags.Groups.ANALYTICS) {
            migration<CreateManagerEvents>()
        }
    }
}

private fun runMigrations(
    label: String,
    handle: DatabaseHandleApi,
    controller: MigrationController,
) {
    runCommand("$label system migration initialization", handle) { controller.init(it) }
    runMigrationBlock("$label migration commands", handle) {
        requireMigrationSuccess("$label migrate", migrate())
        reportMigrationState(label, status(), current())

        // A second call demonstrates that persisted history makes the configured migration a no-op.
        requireMigrationSuccess("$label repeated migrate", migrate())
        reportMigrationState("$label after repeated migrate", status(), current())
    }
}

private fun rollbackAndRemigrate(
    label: String,
    handle: DatabaseHandleApi,
) {
    runMigrationBlock("$label rollback and re-migration", handle) {
        requireMigrationSuccess("$label rollback", rollback())
        reportMigrationState("$label after rollback", status(), current())
        requireMigrationSuccess("$label re-migrate", migrate())
        reportMigrationState("$label after re-migrate", status(), current())
    }
}

private fun runMigrationBlock(
    label: String,
    handle: DatabaseHandleApi,
    block: MigrationFacade.() -> Unit,
) {
    requireValue(label, handle.migration(block))
}

private fun requireMigrationSuccess(label: String, result: ExecuteResult<Boolean>) {
    val completed = requireValue(label, result)
    check(completed) { "$label returned an unsuccessful result" }
    println("$label: completed")
}

private fun runCommand(
    label: String,
    handle: DatabaseHandleApi,
    command: (gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope) ->
        ExecuteResult<Boolean>,
) {
    val result = handle.withOperationScope(command)
    val completed = requireValue(label, result)
    check(completed) { "$label returned an unsuccessful result" }
    println("$label: completed")
}

private fun reportMigrationState(
    label: String,
    statusResult: ExecuteResult<MigrationStatusReport>,
    currentResult: ExecuteResult<MigrationVersion?>,
) {
    val status = requireValue("$label status", statusResult)
    val current = when (val result = currentResult) {
        is ExecuteResult.Failure -> throw ExampleFailure("$label current version", result.exception)
        is ExecuteResult.Success -> result.result?.name ?: "none"
    }
    val versions = status.versions.joinToString { "${it.name}:${it.status}" }.ifEmpty { "none" }
    println(
        "$label migration state: current=$current, versions=[$versions], " +
            "issues=${status.issues.size}, unknownVersions=${status.unknownVersions.size}, " +
            "unknownMigrations=${status.unknownMigrations.size}",
    )
}

private fun insertAndReadExamples(main: DatabaseHandleApi, analytics: DatabaseHandleApi) {
    val customerRows = requireValue("Main connection queries", main.connection { executor ->
        ensureCustomer(executor)
        selectRows(executor, CUSTOMER_TABLE, "customer_id", "display_name")
    })
    reportRows("Main connection SELECT", customerRows, "customer_id", "display_name")

    val eventRows = requireValue("Analytics connection queries", analytics.connection { executor ->
        ensureEvent(executor)
        selectRows(executor, EVENT_TABLE, "event_id", "event_name")
    })
    reportRows("Analytics connection SELECT", eventRows, "event_id", "event_name")

    val customerTransactionRows = requireValue(
        "Main transaction SELECT",
        main.transaction { executor -> selectRows(executor, CUSTOMER_TABLE, "customer_id", "display_name") },
    )
    reportRows("Main transaction SELECT", customerTransactionRows, "customer_id", "display_name")

    val eventTransactionRows = requireValue(
        "Analytics transaction SELECT",
        analytics.transaction { executor -> selectRows(executor, EVENT_TABLE, "event_id", "event_name") },
    )
    reportRows("Analytics transaction SELECT", eventTransactionRows, "event_id", "event_name")
}

private fun ensureCustomer(executor: IQueryBuilderExecutor) {
    val rows = selectRows(executor, CUSTOMER_TABLE, "customer_id", "display_name").toValue()
    if (rows.none { it.getValue("display_name") == SAMPLE_CUSTOMER }) {
        insertValue(executor, CUSTOMER_TABLE, "display_name", SAMPLE_CUSTOMER).toValue()
    }
}

private fun ensureEvent(executor: IQueryBuilderExecutor) {
    val rows = selectRows(executor, EVENT_TABLE, "event_id", "event_name").toValue()
    if (rows.none { it.getValue("event_name") == SAMPLE_EVENT }) {
        insertValue(executor, EVENT_TABLE, "event_name", SAMPLE_EVENT).toValue()
    }
}

private fun selectRows(
    executor: IQueryBuilderExecutor,
    tableName: String,
    idAlias: String,
    valueColumn: String,
): ExecuteResult<List<QueryRow>> = executor.queryBuilderSelect { query ->
    query.select {
        addColumn {
            column { columnSelector("id") }
            alias(idAlias)
            execute(DataType.INT)
        }
        addColumn {
            column { columnSelector(valueColumn) }
            alias(valueColumn)
            execute(DataType.STRING)
        }
    }
    query.from { table(tableName) }
}.execute()

private fun insertValue(
    executor: IQueryBuilderExecutor,
    tableName: String,
    columnName: String,
    value: String,
): ExecuteResult<Long> = executor.queryBuilder(
    QueryRenderInsertBuilder()
        .table { table(tableName) }
        .addValue {
            column(columnName)
            setValue {
                paramName(columnName)
                paramValue(value)
            }
        },
).execute()

private fun reportRows(
    label: String,
    rows: List<QueryRow>,
    idAlias: String,
    valueColumn: String,
) {
    if (rows.isEmpty()) {
        println("$label: no records")
        return
    }
    rows.forEach { row ->
        println("$label: id=${row.getValue(idAlias)}, $valueColumn=${row.getValue(valueColumn)}")
    }
}

private fun databaseConfig(prefix: String, expectedDatabaseName: String): DatabaseConfig {
    val nameVariable = "${prefix}_DB_NAME"
    val databaseName = requiredEnvironment(nameVariable)
    require(databaseName == expectedDatabaseName) {
        "$nameVariable must be exactly '$expectedDatabaseName' for this dedicated example"
    }

    val user = requiredEnvironment("${prefix}_DB_USER")
    val password = requiredEnvironment("${prefix}_DB_PASSWORD", allowWhitespace = true)
    val portVariable = "${prefix}_DB_PORT"
    val port = System.getenv(portVariable)?.let { rawPort ->
        val parsedPort = rawPort.toIntOrNull()
            ?: throw IllegalArgumentException("$portVariable must be an integer")
        require(parsedPort in 1..65535) { "$portVariable must be between 1 and 65535" }
        parsedPort
    } ?: 3306

    val domainVariable = "${prefix}_DB_DOMAIN"
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

private fun <T : Any> requireValue(label: String, result: ExecuteResult<T>): T = when (result) {
    is ExecuteResult.Failure -> throw ExampleFailure(label, result.exception)
    is ExecuteResult.Success -> result.result ?: throw ExampleFailure(
        label,
        IllegalStateException("Operation completed without a result value"),
    )
}

private fun reportAndFail(failure: Throwable): Nothing {
    System.err.println("Database example failed:")

    generateSequence(failure as Throwable?) { it.cause }
        .forEachIndexed { index, error ->
            System.err.println("Cause #$index: ${error::class.qualifiedName}")
            System.err.println("Message: ${error.message}")

            if (error is java.sql.SQLException) {
                System.err.println("SQLState: ${error.sqlState}")
                System.err.println("ErrorCode: ${error.errorCode}")
            }
        }

    failure.printStackTrace()

    if (failure is Error) {
        throw failure
    }

    throw IllegalStateException("Database example failed", failure)
}

private class ExampleFailure(label: String, cause: Throwable) : RuntimeException(label, cause)
