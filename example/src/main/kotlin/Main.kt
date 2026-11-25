package gog.my_project.example

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base.core.events.DatabaseWatchSubscription
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import gog.my_project.data_base_manager.facade.watch
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import gog.my_project.data_base_migration.core.events.MigrationEvent
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

internal data class ExampleDatabaseAttempt(
    val database: String,
    val failure: Throwable?,
)

internal fun runIndependentDatabaseAttempts(
    onAttempt: (ExampleDatabaseAttempt) -> Unit,
    main: () -> Unit,
    analytics: () -> Unit,
) {
    val mainAttempt = captureDatabaseAttempt("MAIN", main)
    onAttempt(mainAttempt)
    (mainAttempt.failure as? Error)?.let { throw it }

    val analyticsAttempt = captureDatabaseAttempt("ANALYTICS", analytics)
    onAttempt(analyticsAttempt)
    (analyticsAttempt.failure as? Error)?.let { throw it }
}

private fun captureDatabaseAttempt(database: String, action: () -> Unit): ExampleDatabaseAttempt = try {
    action()
    ExampleDatabaseAttempt(database, null)
} catch (failure: Exception) {
    ExampleDatabaseAttempt(database, failure)
} catch (failure: Error) {
    ExampleDatabaseAttempt(database, failure)
}

internal fun combinedDatabaseFailure(attempts: List<ExampleDatabaseAttempt>): Throwable? {
    val failedAttempts = attempts.filter { it.failure != null }
    if (failedAttempts.isEmpty()) return null

    val fatalFailure = failedAttempts.firstNotNullOfOrNull { it.failure as? Error }
    if (fatalFailure != null) {
        failedAttempts.forEach { attempt ->
            val error = requireNotNull(attempt.failure)
            if (error !== fatalFailure) {
                fatalFailure.addSuppressed(
                    ExampleFailure("${attempt.database} database run failed", error),
                )
            }
        }
        return fatalFailure
    }

    val first = failedAttempts.first()
    val combined = ExampleFailure("${first.database} database run failed", requireNotNull(first.failure))
    failedAttempts.drop(1).forEach { attempt ->
        combined.addSuppressed(
            ExampleFailure("${attempt.database} database run failed", requireNotNull(attempt.failure)),
        )
    }
    return combined
}

internal fun combineFailures(primary: Throwable?, secondary: Throwable?): Throwable? {
    if (primary == null) return secondary
    if (secondary == null || primary === secondary) return primary

    if (secondary is Error && primary !is Error) {
        secondary.addSuppressed(primary)
        return secondary
    }

    primary.addSuppressed(secondary)
    return primary
}


/*
    migrate()
    .\gradlew.bat :example:run --project-prop "database.example.run=true"
    rollback()
    .\gradlew.bat :example:run --project-prop "database.example.run=true"  --project-prop "database.example.rollback=true"
    recovery()
    .\gradlew.bat :example:run --project-prop "database.example.run=true" --project-prop "database.example.recovery=true"

    .\gradlew.bat :example:run --project-prop "database.example.run=true" --project-prop "database.example.status=true"
    .\gradlew.bat :example:run --project-prop "database.example.run=true" --project-prop "database.example.state=true"
    .\gradlew.bat :example:run --project-prop "database.example.run=true" --project-prop "database.example.retryUp=true"
    .\gradlew.bat :example:run --project-prop "database.example.run=true" --project-prop "database.example.retryDown=true"
*/


fun main() {

    if (!System.getProperty("database.example.run", "false").toBooleanStrict()) {
        println("Database calls are disabled. Set -Pdatabase.example.run=true to opt in.")
        return
    }
    val rollbackEnabled = System.getProperty("database.example.rollback", "false").toBooleanStrict()
    val recoveryEnabled = System.getProperty("database.example.recovery", "false").toBooleanStrict()
    val statusEnabled = System.getProperty("database.example.status", "false").toBooleanStrict()
    val stateEnabled = System.getProperty("database.example.state", "false").toBooleanStrict()
    val retryUpEnabled = System.getProperty("database.example.retryUp", "false").toBooleanStrict()
    val retryDownEnabled = System.getProperty("database.example.retryDown", "false").toBooleanStrict()
    val retryToCompleteVersion = System.getProperty(
        "database.example.retry.toCompleteVersion",
        "false",
    ).toBooleanStrict()
    val enabledModes = listOf(
        rollbackEnabled,
        recoveryEnabled,
        statusEnabled,
        stateEnabled,
        retryUpEnabled,
        retryDownEnabled,
    ).count { it }
    require(enabledModes <= 1) {
        "Enable at most one of database.example.rollback, recovery, status, state, retryUp, or retryDown"
    }
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
    val databaseAttempts = mutableListOf<ExampleDatabaseAttempt>()
    var mainWatch: DatabaseWatchSubscription? = null
    //var analyticsWatch: DatabaseWatchSubscription? = null
    try {
        runIndependentDatabaseAttempts(
            onAttempt = databaseAttempts::add,
            main = {
                val main = manager[mainDatabase]
                mainWatch = main.watch { event -> reportDatabaseEvent("MAIN", event) }

                if (rollbackEnabled) {
                    println("Rollback opt-in is enabled.")
                    // Rollback removes the application tables, so exercise them before rolling back.
                    insertAndReadMainExamples(main)
                }

                requireValue("Main migration block", main.migration {
                    requireMigrationSuccess("Main init", init())

                    if (statusEnabled) {
                        reportMigrationState("Main status", status(), current())
                    } else if (stateEnabled) {
                        reportVersionState("Main latest state", state(MigrationVersionSelection.LATEST))
                        reportVersionState(
                            "Main latest incomplete state",
                            state(MigrationVersionSelection.LATEST_INCOMPLETE),
                        )
                    } else if (retryUpEnabled) {
                        retryFromLatestState(
                            "Main retryUp",
                            state(MigrationVersionSelection.LATEST_INCOMPLETE),
                            retryToCompleteVersion,
                        ) { target, toCompleteVersion -> retryUp(target, toCompleteVersion) }
                    } else if (retryDownEnabled) {
                        retryFromLatestState(
                            "Main retryDown",
                            state(MigrationVersionSelection.LATEST_INCOMPLETE),
                            retryToCompleteVersion,
                        ) { target, toCompleteVersion -> retryDown(target, toCompleteVersion) }
                    } else if (rollbackEnabled) {
                        println("Starting Main rollback...")
                        requireMigrationSuccess("Main rollback", rollback())
                        reportMigrationState("Main after rollback", status(), current())
                    } else if (recoveryEnabled) {
                        println("Starting Main recovery...")
                        reportRecovery("Main recovery", recovery(true))
                        reportMigrationState("Main after recovery", status(), current())
                    } else {
                        requireMigrationSuccess("Main migrate", migrate())
                        reportMigrationState("Main", status(), current())

                        requireMigrationSuccess("Main repeated migrate", migrate())
                        reportMigrationState("Main after repeated migrate", status(), current())
                    }
                })

                if (!rollbackEnabled && !recoveryEnabled && !statusEnabled && !stateEnabled &&
                    !retryUpEnabled && !retryDownEnabled
                ) {
                    insertAndReadMainExamples(main)
                }
            },
            analytics = {
                val analytics = manager[analyticsDatabase]
                //analyticsWatch = analytics.watch { event -> reportDatabaseEvent("ANALYTICS", event) }

                if (rollbackEnabled) {
                    println("Rollback opt-in is enabled.")
                    // Rollback removes the application tables, so exercise them before rolling back.
                    insertAndReadAnalyticsExamples(analytics)
                }

                requireValue("Analytics migration block", analytics.migration {
                    requireMigrationSuccess("Analytics init", init())

                    if (statusEnabled) {
                        reportMigrationState("Analytics status", status(), current())
                    } else if (stateEnabled) {
                        reportVersionState("Analytics latest state", state(MigrationVersionSelection.LATEST))
                        reportVersionState(
                            "Analytics latest incomplete state",
                            state(MigrationVersionSelection.LATEST_INCOMPLETE),
                        )
                    } else if (retryUpEnabled) {
                        retryFromLatestState(
                            "Analytics retryUp",
                            state(MigrationVersionSelection.LATEST_INCOMPLETE),
                            retryToCompleteVersion,
                        ) { target, toCompleteVersion -> retryUp(target, toCompleteVersion) }
                    } else if (retryDownEnabled) {
                        retryFromLatestState(
                            "Analytics retryDown",
                            state(MigrationVersionSelection.LATEST_INCOMPLETE),
                            retryToCompleteVersion,
                        ) { target, toCompleteVersion -> retryDown(target, toCompleteVersion) }
                    } else if (rollbackEnabled) {
                        println("Starting Analytics rollback...")
                        requireMigrationSuccess("Analytics rollback", rollback())
                        reportMigrationState("Analytics after rollback", status(), current())
                    } else if (recoveryEnabled) {
                        println("Starting Analytics recovery...")
                        reportRecovery("Analytics recovery", recovery(true))
                        reportMigrationState("Analytics after recovery", status(), current())
                    } else {
                        requireMigrationSuccess("Analytics migrate", migrate())
                        reportMigrationState("Analytics", status(), current())

                        requireMigrationSuccess("Analytics repeated migrate", migrate())
                        reportMigrationState("Analytics after repeated migrate", status(), current())
                    }
                })

                if (!rollbackEnabled && !recoveryEnabled && !statusEnabled && !stateEnabled &&
                    !retryUpEnabled && !retryDownEnabled
                ) {
                    insertAndReadAnalyticsExamples(analytics)
                }
            },
        )


    } catch (error: Throwable) {
        failure = error
    } finally {
        //failure = closeWatchSubscription(analyticsWatch, failure)
        failure = closeWatchSubscription(mainWatch, failure)
        try {
            manager.shutdown()
        } catch (cleanupFailure: Throwable) {
            failure = combineFailures(failure, cleanupFailure)
        }
    }

    reportDatabaseAttempts(databaseAttempts)
    failure = combineFailures(failure, combinedDatabaseFailure(databaseAttempts))
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

private fun reportDatabaseAttempts(attempts: List<ExampleDatabaseAttempt>) {
    attempts.forEach { attempt ->
        val error = attempt.failure
        if (error == null) {
            println("[${attempt.database}] migration result: SUCCESS")
        } else {
            println(
                "[${attempt.database}] migration result: FAILURE " +
                    "(${error::class.qualifiedName}: ${error.message})",
            )
        }
    }
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
        combineFailures(primaryFailure, cleanupFailure)
    }
}

private fun mainDatabaseMigrations(): MigrationConfiguration = migrationConfig {
    migrationVersion(DataBaseMigrationTags.Versions.Init) {
        migration<CreateManagerCustomers>()
        migration<CreateManagerEvents>()
    }
}

private fun analyticsDatabaseMigrations(): MigrationConfiguration = migrationConfig {
    migrationVersion(DataBaseMigrationTags.Versions.Init) {
        migrationGroup(DataBaseMigrationTags.Groups.ANALYTICS) {
            migration<CreateManagerEvents>()
        }
    }
}

private fun requireMigrationSuccess(label: String, result: ExecuteResult<Boolean>) {
    val completed = requireValue(label, result)
    check(completed) { "$label returned an unsuccessful result" }
    println("$label: completed")
}

private fun reportRecovery(label: String, result: ExecuteResult<Boolean>) {
    when (result) {
        is ExecuteResult.Failure -> throwResultFailure(label, result.exception)
        is ExecuteResult.Success -> when (result.result) {
            null -> println("$label: no incomplete Migration Version; nothing to recover")
            true -> println("$label: completed")
            false -> println("$label: stopped before completing the Version")
        }
    }
}

private fun reportVersionState(
    label: String,
    result: ExecuteResult<MigrationRecoveryTarget?>,
) {
    when (result) {
        is ExecuteResult.Failure -> throwResultFailure(label, result.exception)
        is ExecuteResult.Success -> {
            val target = result.result
            if (target == null) {
                println("$label: no Version selected")
                return
            }

            val state = target.version
            println(
                "$label: versionId=${state.versionId}, tagId=${state.tagId}, status=${state.status}, " +
                    "operation=${target.operation}, migrationId=${target.migration?.id?.value ?: "unresolved"}, " +
                    "checkpointRevision=${target.checkpointRevision ?: "unknown"}",
            )
            state.message?.let { println("$label diagnostic: $it") }
        }
    }
}

private fun retryFromLatestState(
    label: String,
    stateResult: ExecuteResult<MigrationRecoveryTarget?>,
    toCompleteVersion: Boolean,
    retry: (MigrationRecoveryTarget, Boolean) -> ExecuteResult<Boolean>,
) {
    when (stateResult) {
        is ExecuteResult.Failure -> throwResultFailure("$label state", stateResult.exception)
        is ExecuteResult.Success -> {
            val target = stateResult.result
            if (target == null) {
                println("$label: no incomplete Version; Retry was not invoked")
                return
            }

            println(
                "$label target: versionId=${target.version.versionId}, status=${target.version.status}, " +
                    "operation=${target.operation}, migrationId=${target.migration?.id?.value ?: "unresolved"}, " +
                    "checkpointRevision=${target.checkpointRevision ?: "unknown"}",
            )
            reportRetryResult(label, retry(target, toCompleteVersion))
        }
    }
}

private fun reportRetryResult(label: String, result: ExecuteResult<Boolean>) {
    when (result) {
        is ExecuteResult.Failure -> throwResultFailure(label, result.exception)
        is ExecuteResult.Success -> when (result.result) {
            null -> println("$label: no result value")
            true -> println("$label: Version completed")
            false -> println("$label: stopped at the next Version boundary")
        }
    }
}

private fun reportMigrationState(
    label: String,
    statusResult: ExecuteResult<MigrationStatusReport>,
    currentResult: ExecuteResult<MigrationVersion?>,
) {
    val status = requireValue("$label status", statusResult)
    val current = when (val result = currentResult) {
        is ExecuteResult.Failure -> throwResultFailure("$label current version", result.exception)
        is ExecuteResult.Success -> result.result?.name ?: "none"
    }
    val versions = status.versions.joinToString { "${it.name}:${it.status}" }.ifEmpty { "none" }
    println(
        "$label migration state: current=$current, versions=[$versions], " +
            "issues=${status.issues.size}, unknownVersions=${status.unknownVersions.size}, " +
            "unknownMigrations=${status.unknownMigrations.size}",
    )
}

private fun insertAndReadMainExamples(main: DatabaseHandleApi) {
    val customerRows = requireValue("Main connection queries", main.connection { executor ->
        ensureCustomer(executor)
        selectRows(executor, CUSTOMER_TABLE, "customer_id", "display_name")
    })
    reportRows("Main connection SELECT", customerRows, "customer_id", "display_name")

    val customerTransactionRows = requireValue(
        "Main transaction SELECT",
        main.transaction { executor -> selectRows(executor, CUSTOMER_TABLE, "customer_id", "display_name") },
    )
    reportRows("Main transaction SELECT", customerTransactionRows, "customer_id", "display_name")
}

private fun insertAndReadAnalyticsExamples(analytics: DatabaseHandleApi) {
    val eventRows = requireValue("Analytics connection queries", analytics.connection { executor ->
        ensureEvent(executor)
        selectRows(executor, EVENT_TABLE, "event_id", "event_name")
    })
    reportRows("Analytics connection SELECT", eventRows, "event_id", "event_name")

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
    is ExecuteResult.Failure -> throwResultFailure(label, result.exception)
    is ExecuteResult.Success -> result.result ?: throw ExampleFailure(
        label,
        IllegalStateException("Operation completed without a result value"),
    )
}

private fun throwResultFailure(label: String, failure: Throwable): Nothing {
    if (failure is Error) throw failure
    throw ExampleFailure(label, failure)
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
