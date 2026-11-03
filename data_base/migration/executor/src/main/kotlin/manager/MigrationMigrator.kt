package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base.migration.api.interfaces.MigrationGroup
import gog.my_project.data_base.migration.api.interfaces.MigrationRegistration
import gog.my_project.data_base.migration.api.interfaces.RegisteredMigration
import gog.my_project.data_base.migration.api.interfaces.SingleMigration
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.migration.executor.interfaces.execute
import gog.my_project.data_base.migration.executor.internal.system_migration.SystemMigrationStateStore
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.java

/** Runs pending registered migrations in configuration order and records each successful one. */
class MigrationMigrator(
    private val configuration: MigrationConfiguration,
    private val executor: IMigrationExecutor = MigrationExecutor(),
) {
    fun migrate(block: (ExecuteResult<Unit>) -> Unit) {
        val completion = Completion(block)
        if (configuration.registrations.isEmpty()) {
            completion.succeed()
            return
        }

        safely(completion) {
            stateStore().initialize { stateResult ->
                safely(completion) {
                    when (stateResult) {
                        is ExecuteResult.Failure -> completion.fail(stateResult)
                        is ExecuteResult.Success -> {
                            val state = requireNotNull(stateResult.result) {
                                "Migration state initialization returned no state"
                            }
                            val appliedIds = state.history
                                .mapTo(HashSet()) { it.migration }
                            val pending = configuration.orderedMigrations()
                                .filterNot { it.id.value in appliedIds }

                            if (pending.isEmpty()) {
                                completion.succeed()
                            } else {
                                val batch = Math.addExact(state.currentBatch ?: 0, 1)
                                runPending(pending, index = 0, batch = batch, completion = completion)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stateStore(
        queryExecutor: IQueryExecute = executor.queryExecutor,
        migrationExecutor: IMigrationExecutor = executor,
    ): SystemMigrationStateStore = SystemMigrationStateStore(queryExecutor, migrationExecutor)

    private fun runPending(
        pending: List<RegisteredMigration>,
        index: Int,
        batch: Int,
        completion: Completion,
    ) {
        if (completion.isCompleted) return
        if (index >= pending.size) {
            completion.succeed()
            return
        }

        val registered = pending[index]
        safely(completion) {
            val migration = instantiate(registered)
            val definition = migration.up()
            val operation = definition.operations.single()
            val transactionResult = executor.transaction { tx ->
                var result: ExecuteResult<Unit>? = null
                var delivered = false
                tx.execute(operation, blockExecute = { executionResult ->
                    check(!delivered) { "Migration operation completed more than once" }
                    delivered = true
                    when (executionResult) {
                        is ExecuteResult.Failure -> result = executionResult
                        is ExecuteResult.Success -> {
                            if (executionResult.result != true) {
                                result = ExecuteResult.Failure(
                                    IllegalStateException(
                                        "Migration '${registered.id.value}' operation did not report Success(true)",
                                    ),
                                )
                            } else {
                                stateStore(tx.queryExecutor, tx)
                                    .recordSuccessfulMigration(registered.id.value, batch) { recordResult ->
                                        check(result == null) { "Migration history recording completed more than once" }
                                        result = when (recordResult) {
                                            is ExecuteResult.Failure -> recordResult
                                            is ExecuteResult.Success -> ExecuteResult.Success(Unit)
                                        }
                                    }
                            }
                        }
                    }
                })
                result ?: ExecuteResult.Failure(
                    IllegalStateException("Migration transaction callbacks must complete synchronously"),
                )
            }

            when (transactionResult) {
                is ExecuteResult.Failure -> completion.fail(transactionResult)
                is ExecuteResult.Success -> runPending(
                    pending = pending,
                    index = index + 1,
                    batch = batch,
                    completion = completion,
                )
            }
        }
    }

    private fun instantiate(registered: RegisteredMigration): Migration = try {
        registered.migrationClass.java.getConstructor().newInstance()
    } catch (error: InvocationTargetException) {
        throw error.targetException ?: error
    }

    private fun MigrationConfiguration.orderedMigrations(): List<RegisteredMigration> =
        buildList {
            registrations.forEach { registration ->
                when (registration) {
                    is SingleMigration -> add(registration.migration)
                    is MigrationGroup -> addAll(registration.migrations)
                }
            }
        }

    private inline fun safely(completion: Completion, action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            if (completion.isCompleted) throw error
            completion.fail(ExecuteResult.Failure(error))
        }
    }

    private class Completion(private val callback: (ExecuteResult<Unit>) -> Unit) {
        private val completed = AtomicBoolean(false)

        val isCompleted: Boolean
            get() = completed.get()

        fun succeed() = complete(ExecuteResult.Success(Unit))

        fun fail(result: ExecuteResult.Failure) = complete(result)

        private fun complete(result: ExecuteResult<Unit>) {
            if (completed.compareAndSet(false, true)) callback(result)
        }
    }
}
