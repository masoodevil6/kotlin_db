# Two-database Migration API example

This runnable sample demonstrates the public Database Manager and Migration handle APIs with two independent MySQL/MariaDB registrations:

- `main_example` registers `CreateManagerCustomers` and owns `manager_customers(id, display_name)`.
- `analytics_example` registers `CreateManagerEvents` under the `analytics` group and owns `manager_events(id, event_name)`.

Each Database Registration receives its own `MigrationConfiguration`. The sample attaches each configuration in the registration DSL, then obtains the corresponding `DatabaseHandleApi` and invokes commands through `handle.migration { ... }`. It does not build a `MigrationController` or use the Operation Scope as a consumer entry point.

```kotlin
val manager: DatabaseManagerApi = DatabaseManager {
    migrationTags = DataBaseMigrationTags
    database(MainDatabase) {
        config = mainConfig
        migrationConfiguration = mainMigrations
    }
    database(analyticsDatabase) {
        config = analyticsConfig
        migrationConfiguration = analyticsMigrations
    }
}

val main = manager[MainDatabase]
val result = main.migration { migrate() }.toValue().toValue()
```

The runnable sample initializes the system tables, applies and repeats each migration, inspects `status()` and `current()`, and optionally runs rollback and re-migration. For `migrate(target)`, the target Version is included. For `rollback(target)`, only Versions after the target are rolled back; the target remains applied. The actual sample has one Version per database, so target-based rollback has no later Version to remove.

For the full command contract, status codes, Global Failure Gate, SystemLock, and failure limitations, see the [Migration execution guide](../documents/guides/migration/executor/README.md). The focused Schema-rendering module is [`data_base:migration:example`](../data_base/migration/example/README.md); it does not connect to a database.

The target overloads have different boundaries:

```kotlin
val target = DataBaseMigrationTags.Versions.Init

// Applies pending Versions through and including target.
main.migration { migrate(target) }.toValue().toValue()

// Rolls back Versions newer than target and keeps target applied.
main.migration { rollback(target) }.toValue().toValue()
```

This sample currently configures only `Init`, so the targeted rollback has no newer Version to remove. In an application with later configured Versions, `rollback(target)` keeps `target` and removes only those later Versions.

## Safe default and database setup

No database call is made unless `database.example.run=true` is supplied. The sample then requires two dedicated, disposable databases with exactly these names: `main_example` and `analytics_example`. Do not point it at shared, production, or valuable schemas.

Set the environment variables below in the shell, or put them in the ignored `.env` file at the repository root. The `:example:run` Gradle task loads that file; environment variables already set in the process take precedence. Launching `MainKt` directly does not load `.env`.

```powershell
$env:MAIN_DB_DOMAIN = "jdbc:mysql://127.0.0.1"
$env:MAIN_DB_PORT = "3306"
$env:MAIN_DB_NAME = "main_example"
$env:MAIN_DB_USER = "<main-user>"
$env:MAIN_DB_PASSWORD = "<main-password>"

$env:ANALYTICS_DB_DOMAIN = "jdbc:mysql://127.0.0.1"
$env:ANALYTICS_DB_PORT = "3306"
$env:ANALYTICS_DB_NAME = "analytics_example"
$env:ANALYTICS_DB_USER = "<analytics-user>"
$env:ANALYTICS_DB_PASSWORD = "<analytics-password>"
```

The domain and port default to `jdbc:mysql://127.0.0.1` and `3306`. Database names, usernames, and passwords are required. The example does not print the configuration values directly; failure diagnostics include exception messages and stack traces, so do not put secrets in exception text.

The default run only reports that database calls are disabled:

```powershell
.gradlew.bat :example:run
```

Compile and test without running against a database:

```powershell
.gradlew.bat :example:compileKotlin :example:compileTestKotlin :example:test
```

Explicitly opt in to system-table initialization, migrations, and sample Query operations:

```powershell
.gradlew.bat :example:run -Pdatabase.example.run=true
```

This creates the three system migration tables if absent, runs the configured application migrations, reports migration state, and performs the sample inserts and SELECTs. The sample checks for its representative row before inserting it.

Rollback is a separate destructive opt-in. It drops the two application tables and their data before re-migrating them:

```powershell
.gradlew.bat :example:run -Pdatabase.example.run=true -Pdatabase.example.rollback=true
```

The Migration inspection and retry modes run for both registrations. Enable only one command mode per invocation. `status` reports the status analysis, while `state` prints both the latest Version and latest incomplete Version, including the stored diagnostic. Retry modes select the latest incomplete state and pass that exact target to the corresponding public Retry API. `toCompleteVersion` defaults to `false`, so Retry stops at the next Version boundary unless explicitly enabled.

```powershell
.gradlew.bat :example:run -Pdatabase.example.run=true -Pdatabase.example.status=true
.gradlew.bat :example:run -Pdatabase.example.run=true -Pdatabase.example.state=true
.gradlew.bat :example:run -Pdatabase.example.run=true -Pdatabase.example.retryUp=true
.gradlew.bat :example:run -Pdatabase.example.run=true -Pdatabase.example.retryDown=true
.gradlew.bat :example:run -Pdatabase.example.run=true -Pdatabase.example.retryUp=true -Pdatabase.example.retry.toCompleteVersion=true
```

Retry can execute Migration code. Use these modes only with the dedicated disposable databases above, after inspecting their reported state and confirming that the requested direction is appropriate.

Use only the two empty, disposable databases named above. Rollback does not drop the system migration tables. Both Watch subscriptions are closed before `manager.shutdown()`. Ordinary cleanup failures are attached as suppressed exceptions to the existing failure; a fatal cleanup `Error` remains fatal and carries the earlier failure as suppressed.

The repository's root example is an in-workspace runnable sample. Its source demonstrates the public Handle API, but its existing Gradle file declares additional project dependencies; use only the aggregate `:data_base:migration` dependency in an independent consumer. A separate Stage 4 consumer project compiled with only that aggregate dependency. Published Maven coordinates and binary compatibility remain unverified.
