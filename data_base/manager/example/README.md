# Database Manager Consumer Example

This module is an external-style consumer of `:data_base:manager`. Its only direct project dependency is the public Manager module; Query, Schema, and Migration APIs are available through Manager's published API dependencies.

`DatabaseManager` owns a catalog of registrations and lazily creates one `DatabaseContext` for a registration when its handle is first used. `DatabaseKey` identifies a registration; use stable singleton keys such as `MainDatabase` and `AnalyticsDatabase`. Each registration has its own `DatabaseConfig`, and may have its own `MigrationConfiguration`.

`DatabaseHandle` exposes four entry points:

- `query { executor -> ... }` runs Query work in one Connection Scope.
- `transaction { tx -> ... }` runs Query work in an Operation-owned Transaction Scope.
- `schema(operation)` executes one public `SchemaOperation` against that database.
- `migrate { result -> ... }` runs only migrations assigned to that registration.

The example registers `MainDatabase` with a customer-table migration and `AnalyticsDatabase` without migrations. It then shows a Query, a transactional Query, and a Schema operation through their separate handles. `shutdown()` closes Manager-owned contexts and prevents further use; operation Connections remain owned by their Query, Schema, or Migration scopes.

## Run

The default run is safe and only prints the registered keys; it does not open a Connection:

```powershell
.\gradlew.bat :data_base:manager:example:run
```

Database calls are opt-in. Use isolated development schemas, never production schemas. Before opting in, provide `MAIN_DB_NAME`, `MAIN_DB_USER`, `MAIN_DB_PASSWORD`, `ANALYTICS_DB_NAME`, `ANALYTICS_DB_USER`, and `ANALYTICS_DB_PASSWORD`. Optional connection settings are `MAIN_DB_DOMAIN`, `MAIN_DB_PORT`, `ANALYTICS_DB_DOMAIN`, and `ANALYTICS_DB_PORT`; the default domain is `jdbc:mysql://127.0.0.1` and the default port is `3306`. Passwords are read from the environment and are not printed.

```powershell
.\gradlew.bat :data_base:manager:example:run -Pdatabase.example.run=true
```

The opt-in run performs real DDL and Query work: the Main registration applies its Migration and reads the resulting table, while the Analytics registration creates its example events table. The default run and unit tests require no database.
