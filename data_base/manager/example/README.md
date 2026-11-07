# Database Manager Consumer Example

This module consumes the public `:data_base:manager` API. It demonstrates typed database keys, independent configurations, Query execution, Transaction execution, and Manager shutdown. The Manager does not currently expose migration registration or execution while the Migration runtime is being rewritten.

`DatabaseManagerApi` and `DatabaseHandleApi` are the consumer-facing contracts. The manager owns the catalog and lazily creates a context for each registration. `DatabaseKey` identifies a registration. Each handle exposes `connection { ... }` and `transaction { ... }`; both use Operation-managed scopes. `shutdown()` closes Manager-owned contexts and rejects later work.

The Query examples expect the referenced tables to exist already. This module has no Migration dependency.

## Run

The default run only prints registered keys and does not open a Connection:

```powershell
.\gradlew.bat :data_base:manager:example:run
```

Database calls are opt-in. Use isolated development databases and provide the documented `MAIN_DB_*` and `ANALYTICS_DB_*` environment variables before opting in:

```powershell
.\gradlew.bat :data_base:manager:example:run -Pdatabase.example.run=true
```

The opt-in run executes Query and Transaction examples against the pre-existing Main customer table. It does not create or alter tables.
