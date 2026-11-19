# Database Manager Consumer Example

This module consumes the public `:data_base:manager` API. It demonstrates typed database keys, independent configurations, Query execution, Transaction execution, and Manager shutdown. The Manager does not currently expose migration registration or execution while the Migration runtime is being rewritten.

`DatabaseManagerApi` and `DatabaseHandleApi` are the consumer-facing contracts. The manager owns the catalog and lazily creates a context for each registration. `DatabaseKey` identifies a registration. Each handle exposes `connection { ... }` and `transaction { ... }`; both use Operation-managed scopes. `shutdown()` closes Manager-owned contexts and rejects later work.

## Operation Definition concurrency

The typed Operation Definition extensions, `executeConnectionDefinition(...)` and `executeTransactionDefinition(...)`, run through the managed Handle and its Manager operation lease. Each `DatabaseHandle` owns one Definition `OperationExecutionCoordinator`; its Connection and Transaction Definitions share that Coordinator. Concurrent Definition calls on the same Handle therefore share admission and rejection routing. A separate Handle has its own Definition Coordinator, even when both Handles resolve the same Registration and `DatabaseContext`.

Definition execution and legacy QueryBuilder execution use separate in-process Coordinator lineages. They do not serialize or reject one another across different threads. On the same thread, nested managed operations are still rejected by `OperationScopeGuard` and the rejection is recorded against the active operation. Legacy and Definition operations on different threads may each open and use their own JDBC Connection.

A Manager operation lease protects the Manager's lifetime: shutdown stops new leases, waits for accepted operations to finish resource cleanup, then closes Manager-owned contexts. It is an active-operation count, not a mutual-exclusion lock. A Coordinator controls admission only within its own execution lineage. Database-level locks coordinate operations only when each operation explicitly acquires the same database lock; neither a Manager lease nor separate Coordinators provide that database-wide guarantee.

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
