# Two-database migration lifecycle example

This runnable example uses the public Database Manager, Migration Controller, Operation Scope,
and Query DSL APIs with two independent MySQL/MariaDB databases:

- `main_example` owns `manager_customers(id, display_name)`.
- `analytics_example` owns `manager_events(id, event_name)`.

Main and Analytics each have their own Migration Configuration and Controller. The Main
configuration registers only `CreateManagerCustomers`; the Analytics configuration registers
only `CreateManagerEvents`. Each command runs through that database handle's
`withOperationScope` extension, so the Migration Controller receives the actual managed root
Operation Scope for the corresponding database.

## Safety and configuration

The application makes no database calls unless explicitly opted in. Configure both databases
through environment variables; the database names are required and must match the dedicated
names shown below. Use empty, dedicated example databases. Do not point this example at shared,
production, or otherwise valuable schemas.

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

`*_DB_DOMAIN` and `*_DB_PORT` default to `jdbc:mysql://127.0.0.1` and `3306`. User and
password values are required. Credentials and exception messages are not printed.

Compile and run the safe default (no connection is opened):

```powershell
.\gradlew.bat :example:compileKotlin :example:compileTestKotlin :example:test
.\gradlew.bat :example:run
```

Opt into initialization, migrations, and sample Query operations:

```powershell
.\gradlew.bat :example:run -Pdatabase.example.run=true
```

The opted-in run initializes the migration storage, migrates each database, inspects status and
current Version, repeats migration to demonstrate idempotent history handling, inserts one
representative row per table only if that sample value is absent, and reads records through both
connection and transaction APIs. Re-running without rollback does not duplicate the sample rows
or Migration history.

Rollback is a separate destructive opt-in:

```powershell
.\gradlew.bat :example:run -Pdatabase.example.run=true -Pdatabase.example.rollback=true
```

This drops the two example application tables and their data as part of rollback, then re-migrates
them. It is only enabled when both database names are explicitly set to `main_example` and
`analytics_example`; use disposable, dedicated databases. The re-migrated tables are populated
and read again to verify the restored schema. Do not enable this option for shared or valuable
data.

The Manager owns shutdown and closes its database contexts in a `finally` path, including when a
Migration or Query operation fails. No real database execution is part of compilation or unit-test
validation.
