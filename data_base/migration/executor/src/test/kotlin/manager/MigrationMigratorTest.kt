package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationGroup
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.api.interfaces.MigrationIdentity
import gog.my_project.data_base.migration.api.interfaces.MigrationRegistration
import gog.my_project.data_base.migration.api.interfaces.RegisteredMigration
import gog.my_project.data_base.migration.api.interfaces.SingleMigration
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.builder.dropTable
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.migration.renderer.manager.DialectSelector
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.ArrayDeque
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationMigratorTest {
    @Test
    fun emptyConfigurationCompletesWithoutTouchingDatabaseOrExecutor() {
        val database = FakeQueryExecute()
        val migrationExecutor = FakeMigrationExecutor(database)
        var result: ExecuteResult<Unit>? = null

        MigrationMigrator(MigrationConfiguration(emptyList()), migrationExecutor.instance)
            .migrate { result = it }

        assertEquals(ExecuteResult.Success(Unit), result)
        assertEquals(0, migrationExecutor.executeCalls)
        assertEquals(0, database.selectCalls)
        assertEquals(0, database.updateCalls)
    }

    @Test
    fun skipsAppliedMigrationsAndPreservesTopLevelAndGroupOrderingWithSharedBatch() {
        Trace.reset()
        val database = FakeQueryExecute(
            initialHistory = listOf(Row(1, "runner_b", 4)),
        )
        val migrationExecutor = FakeMigrationExecutor(database)
        val configuration = configuration(
            single(RunnerA::class),
            MigrationGroup("users", listOf(registered(RunnerB::class), registered(RunnerC::class))),
            single(RunnerD::class),
        )

        val result = migrate(configuration, migrationExecutor)

        assertEquals(ExecuteResult.Success(Unit), result)
        assertEquals(listOf("a", "c", "d"), migrationExecutor.operationTableNames())
        assertEquals(
            listOf("construct:a", "up:a", "construct:c", "up:c", "construct:d", "up:d"),
            Trace.events,
        )
        assertEquals(listOf("runner_a", "runner_c", "runner_d"), database.recorded.map { it.first })
        assertEquals(listOf(5, 5, 5), database.recorded.map { it.second })
        assertTrue(Trace.events.none { it.startsWith("down:") })
    }

    @Test
    fun allAppliedConfigurationDoesNotInstantiateOrAllocateAnotherBatch() {
        Trace.reset()
        val database = FakeQueryExecute(
            initialHistory = listOf(
                Row(1, "runner_a", 7),
                Row(2, "runner_b", 9),
            ),
        )
        val migrationExecutor = FakeMigrationExecutor(database)

        val result = migrate(configuration(single(RunnerA::class), single(RunnerB::class)), migrationExecutor)

        assertEquals(ExecuteResult.Success(Unit), result)
        assertTrue(Trace.events.isEmpty())
        assertTrue(migrationExecutor.executedOperations.isEmpty())
        assertTrue(database.recorded.isEmpty())
        assertEquals(0, database.updateCalls)
        assertEquals(1, database.selectCalls)
    }

    @Test
    fun failureStopsSequenceAndNextInvocationResumesAfterRecordedMigration() {
        Trace.reset()
        val database = FakeQueryExecute()
        val migrationExecutor = FakeMigrationExecutor(database).apply {
            operationResults += ExecuteResult.Success(true)
            operationResults += ExecuteResult.Failure(IllegalStateException("second failed"))
        }
        val configuration = configuration(
            single(RunnerA::class),
            MigrationGroup("group", listOf(registered(RunnerB::class), registered(RunnerC::class))),
            single(RunnerD::class),
        )

        val first = migrate(configuration, migrationExecutor)
        assertIs<ExecuteResult.Failure>(first)
        assertEquals(listOf("a", "b"), migrationExecutor.operationTableNames())
        assertEquals(listOf("runner_a"), database.recorded.map { it.first })
        assertEquals(listOf(1), database.recorded.map { it.second })

        migrationExecutor.beginInvocation()
        val second = migrate(configuration, migrationExecutor)

        assertEquals(ExecuteResult.Success(Unit), second)
        assertEquals(listOf("a", "b", "b", "c", "d"), migrationExecutor.operationTableNames())
        assertEquals(
            listOf("runner_a", "runner_b", "runner_c", "runner_d"),
            database.recorded.map { it.first },
        )
        assertEquals(listOf(1, 2, 2, 2), database.recorded.map { it.second })
    }

    @Test
    fun failureOnFirstPendingMigrationStopsTheEntireSequence() {
        val database = FakeQueryExecute()
        val migrationExecutor = FakeMigrationExecutor(database).apply {
            operationResults += ExecuteResult.Failure(IllegalStateException("first failed"))
        }

        val result = migrate(
            configuration(single(RunnerA::class), single(RunnerB::class)),
            migrationExecutor,
        )

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("a"), migrationExecutor.operationTableNames())
        assertTrue(database.recorded.isEmpty())
    }

    @Test
    fun upRunsOnceAndDownIsNeverCalled() {
        Trace.reset()
        val migrationExecutor = FakeMigrationExecutor(FakeQueryExecute())

        val result = migrate(configuration(single(RunnerA::class)), migrationExecutor)

        assertEquals(ExecuteResult.Success(Unit), result)
        assertEquals(1, Trace.events.count { it == "construct:a" })
        assertEquals(1, Trace.events.count { it == "up:a" })
        assertFalse(Trace.events.any { it.startsWith("down:") })
    }

    @Test
    fun emptyAndMultipleOperationDefinitionsFailBeforeDispatchOrRecording() {
        listOf(
            configuration(single(ZeroOperationMigration::class)),
            configuration(single(MultipleOperationsMigration::class)),
        ).forEach { configuration ->
            val database = FakeQueryExecute()
            val migrationExecutor = FakeMigrationExecutor(database)

            val result = migrate(configuration, migrationExecutor)

            assertIs<ExecuteResult.Failure>(result)
            assertTrue(migrationExecutor.executedOperations.isEmpty())
            assertTrue(database.recorded.isEmpty())
        }
    }

    @Test
    fun parameterizedConstructorIsRejectedAndThrowingConstructorCauseIsPreserved() {
        val noDefaultConstructorExecutor = FakeMigrationExecutor(FakeQueryExecute())
        val noDefaultConstructorResult = migrate(
            configuration(single(ParameterizedMigration::class)),
            noDefaultConstructorExecutor,
        )
        assertIs<ExecuteResult.Failure>(noDefaultConstructorResult)
        assertIs<NoSuchMethodException>(noDefaultConstructorResult.exception)
        assertTrue(noDefaultConstructorExecutor.executedOperations.isEmpty())

        val throwingConstructorExecutor = FakeMigrationExecutor(FakeQueryExecute())
        val throwingConstructorResult = migrate(
            configuration(single(ThrowingConstructorMigration::class)),
            throwingConstructorExecutor,
        )
        assertIs<ExecuteResult.Failure>(throwingConstructorResult)
        assertEquals("constructor failed", throwingConstructorResult.exception.message)
        assertTrue(throwingConstructorExecutor.executedOperations.isEmpty())
    }

    @Test
    fun upExceptionStopsWithoutExecutionOrRecord() {
        val database = FakeQueryExecute()
        val migrationExecutor = FakeMigrationExecutor(database)

        val result = migrate(configuration(single(ThrowingUpMigration::class)), migrationExecutor)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals("up failed", result.exception.message)
        assertTrue(migrationExecutor.executedOperations.isEmpty())
        assertTrue(database.recorded.isEmpty())
    }

    @Test
    fun executorFalseOrFailureStopsBeforeStateRecording() {
        listOf(
            ExecuteResult.Success(false),
            ExecuteResult.Failure(IllegalStateException("executor failed")),
        ).forEach { executorResult ->
            val database = FakeQueryExecute()
            val migrationExecutor = FakeMigrationExecutor(database).apply {
                operationResults += executorResult
            }

            val result = migrate(configuration(single(RunnerA::class)), migrationExecutor)

            assertIs<ExecuteResult.Failure>(result)
            assertTrue(database.recorded.isEmpty())
        }
    }

    @Test
    fun stateRecordFailureReturnsFailureAndLeavesMigrationPending() {
        val recordFailure = ExecuteResult.Failure(IllegalStateException("record failed"))
        val database = FakeQueryExecute(updateResult = recordFailure)
        val migrationExecutor = FakeMigrationExecutor(database)

        val result = migrate(configuration(single(RunnerA::class)), migrationExecutor)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals("record failed", result.exception.message)
        assertEquals(1, migrationExecutor.operationTableNames().size)
        assertTrue(database.history.isEmpty())
    }

    @Test
    fun bootstrapAndHistoryReadFailuresPreventMigrationInstantiation() {
        Trace.reset()
        val bootstrapFailure = ExecuteResult.Failure(IllegalStateException("bootstrap failed"))
        val bootstrapExecutor = FakeMigrationExecutor(FakeQueryExecute()).apply {
            bootstrapResult = bootstrapFailure
        }
        val bootstrapResult = migrate(configuration(single(RunnerA::class)), bootstrapExecutor)
        assertSameFailure(bootstrapFailure, bootstrapResult)
        assertTrue(Trace.events.isEmpty())

        val readFailure = ExecuteResult.Failure(IllegalStateException("read failed"))
        val database = FakeQueryExecute(selectResult = readFailure)
        val readExecutor = FakeMigrationExecutor(database)
        val readResult = migrate(configuration(single(RunnerA::class)), readExecutor)
        assertSameFailure(readFailure, readResult)
        assertTrue(Trace.events.isEmpty())
        assertTrue(readExecutor.executedOperations.isEmpty())
    }

    @Test
    fun batchOverflowFailsBeforeConstructingFirstPendingMigration() {
        Trace.reset()
        val database = FakeQueryExecute(initialHistory = listOf(Row(1, "already_applied", Int.MAX_VALUE)))
        val migrationExecutor = FakeMigrationExecutor(database)

        val result = migrate(configuration(single(RunnerA::class)), migrationExecutor)

        assertIs<ExecuteResult.Failure>(result)
        assertIs<ArithmeticException>(result.exception)
        assertTrue(Trace.events.isEmpty())
        assertTrue(migrationExecutor.executedOperations.isEmpty())
        assertTrue(database.recorded.isEmpty())
    }

    @Test
    fun exactIdentityStringIsRecordedWithoutGroupOrClassMetadata() {
        val database = FakeQueryExecute()
        val migrationExecutor = FakeMigrationExecutor(database)
        val configuration = configuration(
            MigrationGroup("structural-label", listOf(registered(RunnerA::class))),
        )

        assertEquals(ExecuteResult.Success(Unit), migrate(configuration, migrationExecutor))

        assertEquals(listOf("runner_a" to 1), database.recorded)
    }

    private fun migrate(
        configuration: MigrationConfiguration,
        executor: FakeMigrationExecutor,
    ): ExecuteResult<Unit>? {
        executor.beginInvocation()
        var result: ExecuteResult<Unit>? = null
        MigrationMigrator(configuration, executor.instance).migrate {
            assertNull(result, "terminal callback must be delivered once")
            result = it
        }
        return result
    }

    private fun configuration(vararg registrations: MigrationRegistration) =
        MigrationConfiguration(registrations.toList())

    private fun single(type: KClass<out Migration>) = SingleMigration(registered(type))

    private fun registered(type: KClass<out Migration>): RegisteredMigration {
        val annotation = requireNotNull(type.java.getAnnotation(MigrationId::class.java))
        return RegisteredMigration(MigrationIdentity.fromAnnotationValue(annotation.value), type)
    }

    private fun assertSameFailure(
        expected: ExecuteResult.Failure,
        actual: ExecuteResult<Unit>?,
    ) {
        assertIs<ExecuteResult.Failure>(actual)
        assertTrue(expected === actual)
    }

    private fun FakeMigrationExecutor.operationTableNames(): List<String> =
        executedOperations.map { operation ->
            assertIs<IMigrationDropTableApi>(operation).ast.tableName.orEmpty()
        }

    data class Row(val id: Int, val migration: String, val batch: Int)

    private class FakeQueryExecute(
        initialHistory: List<Row> = emptyList(),
        var selectResult: ExecuteResult<*>? = null,
        var updateResult: ExecuteResult<Int> = ExecuteResult.Success(1),
    ) : IQueryExecute {
        val history = initialHistory.toMutableList()
        val recorded = mutableListOf<Pair<String, Int>>()
        var selectCalls = 0
        var updateCalls = 0

        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
            selectCalls++
            val failure = selectResult
            if (failure is ExecuteResult.Failure) {
                blockExecute(failure)
            } else {
                blockExecute(ExecuteResult.Success(fakeResultSet(history.toList())))
            }
        }

        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
            updateCalls++
            val values = builtQuery.params.associate { it.name to it.value }
            val migration = values["migration"] as? String
                ?: error("State recorder did not bind the migration identity")
            val batch = values["batch"] as? Int
                ?: error("State recorder did not bind the batch")
            recorded += migration to batch

            val result = updateResult
            if (result is ExecuteResult.Success && result.result == 1) {
                history += Row((history.maxOfOrNull(Row::id) ?: 0) + 1, migration, batch)
            }
            blockExecute(result)
        }

        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected executeInsert")

        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected executeDelete")

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) =
            error("State bootstrap must use the injected IMigrationExecutor")
    }

    private class FakeMigrationExecutor(database: IQueryExecute) {
        var bootstrapResult: ExecuteResult<Boolean> = ExecuteResult.Success(true)
        val operationResults = ArrayDeque<ExecuteResult<Boolean>>()
        val executedOperations = mutableListOf<Any>()
        var executeCalls = 0
            private set
        var executeOutCalled = false
            private set
        private var bootstrapNext = true

        val instance: IMigrationExecutor = Proxy.newProxyInstance(
            IMigrationExecutor::class.java.classLoader,
            arrayOf(IMigrationExecutor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getQueryExecutor" -> database
                "getDialectSelector" -> DialectSelector()
                "executeOut" -> {
                    executeOutCalled = true
                    error("MigrationMigrator must use typed execute overloads, not executeOut")
                }
                "execute" -> {
                    executeCalls++
                    val operation = args!![0]
                    @Suppress("UNCHECKED_CAST")
                    val callback = args[1] as (ExecuteResult<Boolean>) -> Unit
                    if (bootstrapNext && operation is gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi) {
                        bootstrapNext = false
                        callback(bootstrapResult)
                    } else {
                        executedOperations += operation
                        callback(if (operationResults.isEmpty()) ExecuteResult.Success(true) else operationResults.removeFirst())
                    }
                    null
                }
                "toString" -> "FakeMigrationExecutor"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === args?.firstOrNull()
                else -> error("Unexpected IMigrationExecutor method ${method.name}")
            }
        } as IMigrationExecutor

        fun beginInvocation() {
            bootstrapNext = true
        }
    }

    private object Trace {
        val events = mutableListOf<String>()
        fun reset() = events.clear()
    }

    abstract class TrackedMigration(private val key: String) : Migration {
        init { Trace.events += "construct:$key" }

        override fun up(): MigrationDefinition {
            Trace.events += "up:$key"
            return migration { dropTable { tableName(key) } }
        }

        override fun down(): MigrationDefinition {
            Trace.events += "down:$key"
            return migration { dropTable { tableName("down_$key") } }
        }
    }

    @MigrationId("runner_a")
    class RunnerA : TrackedMigration("a")

    @MigrationId("runner_b")
    class RunnerB : TrackedMigration("b")

    @MigrationId("runner_c")
    class RunnerC : TrackedMigration("c")

    @MigrationId("runner_d")
    class RunnerD : TrackedMigration("d")

    @MigrationId("runner_zero")
    class ZeroOperationMigration : Migration {
        override fun up() = migration { }
        override fun down() = migration { }
    }

    @MigrationId("runner_multiple")
    class MultipleOperationsMigration : Migration {
        override fun up() = migration {
            dropTable { tableName("first") }
            dropTable { tableName("second") }
        }
        override fun down() = migration { }
    }

    @MigrationId("runner_parameterized")
    class ParameterizedMigration(private val required: String) : Migration {
        override fun up() = migration { dropTable { tableName(required) } }
        override fun down() = migration { }
    }

    @MigrationId("runner_ctor_failure")
    class ThrowingConstructorMigration : Migration {
        init { error("constructor failed") }
        override fun up() = migration { }
        override fun down() = migration { }
    }

    @MigrationId("runner_up_failure")
    class ThrowingUpMigration : Migration {
        override fun up(): MigrationDefinition = error("up failed")
        override fun down() = error("down must not be called")
    }
}

private fun fakeResultSet(rows: List<MigrationMigratorTest.Row>): ResultSet {
    var index = -1
    var closed = false
    return Proxy.newProxyInstance(
        ResultSet::class.java.classLoader,
        arrayOf(ResultSet::class.java),
    ) { _, method, args ->
        when (method.name) {
            "next" -> ++index < rows.size
            "getInt" -> {
                val row = rows[index]
                when (args!![0]) {
                    "id", 1 -> row.id
                    "batch", 3 -> row.batch
                    else -> 0
                }
            }
            "getString" -> {
                val row = rows[index]
                when (args!![0]) {
                    "migration", 2 -> row.migration
                    else -> null
                }
            }
            "close" -> { closed = true; null }
            "isClosed" -> closed
            "wasNull" -> false
            "toString" -> "FakeResultSet"
            "hashCode" -> System.identityHashCode(rows)
            "equals" -> rows === args?.firstOrNull()
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Double.TYPE -> 0.0
                java.lang.Float.TYPE -> 0.0f
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Byte.TYPE -> 0.toByte()
                else -> null
            }
        }
    } as ResultSet
}
