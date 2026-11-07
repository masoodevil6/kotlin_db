# Database Manager root consumer example

This runnable module demonstrates consumption of the public Database Manager, Query, Transaction, Migration-definition, and tag APIs. Manager currently exposes Query connection and transaction operations; Migration registration and execution through Manager are temporarily unavailable while the old runtime is removed for a rewrite. The sample migration definitions remain isolated examples and are not run by this application.

## Prerequisites

The opt-in application connects to the configured MySQL databases and performs Query/Transaction examples only. The Main customer table must already exist. It does not create or alter tables. Provide the `MAIN_DB_*` and `ANALYTICS_DB_*` variables as described below; credentials are read from the environment and are not printed.

## Configure and run

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

Run the safe, non-connecting default, or explicitly opt into database access:

```powershell
.\gradlew.bat :example:run
.\gradlew.bat :example:run -Pdatabase.example.run=true
```

Compiling the module does not execute the application or connect to MySQL. The opt-in command performs Query and Transaction examples against the existing Main table; it does not access migration history or run DDL.
