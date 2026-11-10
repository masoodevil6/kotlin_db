package gog.my_project.data_base_query.executor.manager

import gog.my_project.collection.ICollection
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.DdlFailurePolicy
import gog.my_project.data_base_operation.executor.transaction.DdlExecutionOutcome
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_query.api.interfaces.relations.Cte
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base_query.ast.schema.insert_schema.query_render_insert.QueryRenderInsertAst
import gog.my_project.data_base_query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base_query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base_query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base_query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base_query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base_query.cte.declaration.CteDeclaration
import gog.my_project.data_base_query.executor.result.QueryRow
import java.sql.Connection
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScopeBoundQueryExecutionTest {
    @Test
    fun `scope-only factory retains the exact Scope without constructing a DatabaseContext`() {
        val scope = RecordingOperationContext()
        val executor = QueryBuilderExecutor.scopeBound(scope)

        assertSame(scope.databasePlatformConfig, executor.databasePlatformConfig)
        assertIs<UnsupportedOperationException>(runCatching { executor.databaseContext }.exceptionOrNull())

        val contextField = QueryBuilderExecutor::class.java
            .getDeclaredField("suppliedDatabaseContext")
            .apply { isAccessible = true }
        assertNull(contextField.get(executor))
        val operationExecutorField = QueryBuilderExecutor::class.java
            .getDeclaredField("operationExecutionExecutor")
            .apply { isAccessible = true }
        assertNull(operationExecutorField.get(executor))

        val backendField = QueryBuilderExecutor::class.java
            .getDeclaredField("executionBackend")
            .apply { isAccessible = true }
        val backend = backendField.get(executor)
        val scopeField = backend.javaClass.getDeclaredField("scope").apply { isAccessible = true }
        assertSame(scope, scopeField.get(backend))

        assertIs<ExecuteResult.Failure>(executor.connection { ExecuteResult.Success(true) })
        assertIs<ExecuteResult.Failure>(executor.transaction { ExecuteResult.Success(true) })
        assertFailsWith<UnsupportedOperationException> {
            executor.executeOut(selectQuery()) { _, _ -> error("executeOut must not execute") }
        }
        assertEquals(0, scope.selectCalls + scope.insertCalls + scope.updateCalls + scope.deleteCalls)
    }

    @Test
    fun `scope-only factory runs SELECT mapping and CTE through typed scope execution`() {
        val scope = RecordingOperationContext()
        val executor = QueryBuilderExecutor.scopeBound(scope)
        scope.rows = listOf(mapOf("value" to 31))

        val mapped = executor.queryBuilder(selectWithConditions("repeat", "repeat", 17, 29))
            .executeAs<Int> { row -> row["value"] as Int }

        val mappedValue = assertIs<ExecuteResult.Success<ICollection<Int>>>(mapped).result!!
        assertEquals(listOf(31), buildList { mappedValue.forEach { add(it) } })
        val bound = assertIs<QueryExecutionStatement.Bound>(scope.statements.single()).query
        assertEquals(listOf("repeat", "repeat"), bound.parameterOccurrences.map { it.name })
        assertEquals(listOf(17, 29), bound.parameterOccurrences.map { it.value })
        assertEquals(listOf(Types.INTEGER, Types.INTEGER), bound.parameterOccurrences.map { it.sqlType })
        assertEquals(1, scope.selectCalls)

        scope.statements.clear()
        scope.rows = listOf(mapOf("value" to 42), mapOf("value" to 43))
        val cteResult: ExecuteResult<ICollection<ScopeTestCte>> = ScopeTestCte().execute(executor, Unit)
        val cteValues = assertIs<ExecuteResult.Success<ICollection<ScopeTestCte>>>(cteResult).result!!
        assertEquals(listOf<Int?>(42, 43), buildList { cteValues.forEach { add(it.value) } })
        assertIs<QueryExecutionStatement.Bound>(scope.statements.single())
        assertEquals(2, scope.selectCalls)
    }

    @Test
    fun `scope-only factory routes all DML through typed Operation execution and preserves binding types`() {
        val scope = RecordingOperationContext()
        val executor = QueryBuilderExecutor.scopeBound(scope)
        var inserted: ExecuteResult<Long>? = null
        var updated: ExecuteResult<Int>? = null
        var deleted: ExecuteResult<Int>? = null

        executor.execute(insertQuery(), { inserted = it })
        executor.execute(updateQuery(), { updated = it })
        executor.execute(deleteQuery(), { deleted = it })

        assertEquals(901L, assertIs<ExecuteResult.Success<Long>>(inserted).result)
        assertEquals(2, assertIs<ExecuteResult.Success<Int>>(updated).result)
        assertEquals(3, assertIs<ExecuteResult.Success<Int>>(deleted).result)
        assertEquals(listOf(1, 1, 1), listOf(scope.insertCalls, scope.updateCalls, scope.deleteCalls))
        scope.statements.clear()
        val nullableInsert = QueryRenderInsertBuilder().apply {
            ast = QueryRenderInsertAst().apply {
                table = QueryTableAst().apply { metadata.nameFromAst = "records" }
                columns += QueryColumnInsertAst().apply {
                    metadata.columnName = "nullable_value"
                    metadata.columnTag = "nullable_value"
                    parameterAst = QueryParameterAst<Any?>().apply {
                        metadata.name = "nullable_value"
                        metadata.value = null
                        metadata.sqlType = Types.INTEGER
                    }
                }
            }
        }
        executor.execute(nullableInsert, {})

        val binding = assertIs<QueryExecutionStatement.Bound>(scope.statements.single())
            .query.parameterOccurrences.single()
        assertNull(binding.value)
        assertEquals(Types.INTEGER, binding.sqlType)
    }

    @Test
    fun `scope-only factory propagates SQL failures and fatal Errors to the supplied Scope`() {
        val scope = RecordingOperationContext()
        val executor = QueryBuilderExecutor.scopeBound(scope)
        val sqlFailure = IllegalStateException("scope-only SQL failure")
        scope.nextSelectFailure = sqlFailure

        val failure = executor.queryBuilder(selectQuery()).execute()
        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(failure).exception)
        assertSame(sqlFailure, scope.failures.single())

        scope.failures.clear()
        val fatal = AssertionError("scope-only fatal query error")
        scope.nextSelectError = fatal
        assertSame(fatal, runCatching { executor.queryBuilder(selectQuery()).execute() }.exceptionOrNull())
        assertSame(fatal, scope.failures.single())

        scope.failures.clear()
        val dmlFailure = IllegalStateException("scope-only DML failure")
        scope.nextUpdateFailure = dmlFailure
        var dmlResult: ExecuteResult<Int>? = null
        executor.execute(updateQuery(), { dmlResult = it })
        assertSame(dmlFailure, assertIs<ExecuteResult.Failure>(dmlResult).exception)
        assertSame(dmlFailure, scope.failures.single())
    }

    @Test
    fun `scope-bound executor uses Scope platform and renders a legacy BuiltQuery SELECT`() {
        val fixture = fixture()
        val select = selectQuery()
        fixture.scope.rows = listOf(mapOf("value" to 11))

        val result = fixture.executor.queryBuilder(select).execute()

        val success = assertIs<ExecuteResult.Success<List<QueryRow>>>(result)
        assertEquals(11, success.result!!.single()["value"])
        val executed = assertIs<QueryExecutionStatement.Built>(fixture.scope.statements.single())
        assertTrue(executed.query.query.orEmpty().uppercase().contains("SELECT"))
        assertSame(fixture.context, fixture.executor.databaseContext)
        assertSame(fixture.scope.databasePlatformConfig, fixture.executor.databasePlatformConfig)
        assertNotSame(fixture.context.platformConfig, fixture.executor.databasePlatformConfig)
        val lifecycleExecutorField = QueryBuilderExecutor::class.java
            .getDeclaredField("operationExecutionExecutor")
            .apply { isAccessible = true }
        assertNull(lifecycleExecutorField.get(fixture.executor))
        assertEquals(0, fixture.provider.buildCalls)
    }

    @Test
    fun `scope-bound SELECT uses BoundQuery ordering and preserves duplicate nullable rows`() {
        val fixture = fixture()
        val query = selectWithConditions("first", "second")
        fixture.scope.rows = listOf(
            mapOf("value" to 5),
            mapOf("value" to null),
            mapOf("value" to 5),
        )

        val result = fixture.executor.queryBuilder(query).execute()

        val rows = assertIs<ExecuteResult.Success<List<QueryRow>>>(result).result!!
        assertEquals(listOf(5, null, 5), rows.map { it["value"] })
        val bound = assertIs<QueryExecutionStatement.Bound>(fixture.scope.statements.single()).query
        assertEquals(listOf("first", "second"), bound.parameterOccurrences.map { it.name })
        assertEquals(listOf(7, 7), bound.parameterOccurrences.map { it.value })
        assertTrue(bound.jdbcSql.count { it == '?' } == 2)
        assertEquals(0, fixture.provider.buildCalls)
    }

    @Test
    fun `scope-bound INSERT UPDATE and DELETE delegate to typed Operation methods`() {
        val fixture = fixture()
        var inserted: ExecuteResult<Long>? = null
        var updated: ExecuteResult<Int>? = null
        var deleted: ExecuteResult<Int>? = null

        fixture.executor.execute(insertQuery(), { inserted = it })
        fixture.executor.execute(updateQuery(), { updated = it })
        fixture.executor.execute(deleteQuery(), { deleted = it })

        assertEquals(901L, assertIs<ExecuteResult.Success<Long>>(inserted).result)
        assertEquals(2, assertIs<ExecuteResult.Success<Int>>(updated).result)
        assertEquals(3, assertIs<ExecuteResult.Success<Int>>(deleted).result)
        assertEquals(listOf("INSERT", "UPDATE", "DELETE"), fixture.scope.statements.map { statement ->
            when (statement) {
                is QueryExecutionStatement.Built -> statement.query.query!!.trimStart().substringBefore(' ').uppercase()
                is QueryExecutionStatement.Bound -> statement.query.jdbcSql.trimStart().substringBefore(' ').uppercase()
            }
        })
        assertEquals(1, fixture.scope.insertCalls)
        assertEquals(1, fixture.scope.updateCalls)
        assertEquals(1, fixture.scope.deleteCalls)
        assertEquals(0, fixture.provider.buildCalls)
    }

    @Test
    fun `scope-bound parameter-free DELETE retains the legacy BuiltQuery route`() {
        val fixture = fixture()

        fixture.executor.execute(QueryRenderDeleteBuilder().table { table("records") }, {})

        val built = assertIs<QueryExecutionStatement.Built>(fixture.scope.statements.single()).query
        assertTrue(built.query.orEmpty().uppercase().contains("DELETE"))
        assertEquals(emptyList(), built.params)
        assertEquals(1, fixture.scope.deleteCalls)
        assertEquals(0, fixture.provider.buildCalls)
    }

    @Test
    fun `scope-bound parameter binding preserves nullable value SQL type and repeated occurrences`() {
        val fixture = fixture()
        val nullableInsert = QueryRenderInsertBuilder().apply {
            ast = QueryRenderInsertAst().apply {
                table = QueryTableAst().apply { metadata.nameFromAst = "records" }
                columns += QueryColumnInsertAst().apply {
                    metadata.columnName = "nullable_value"
                    metadata.columnTag = "nullable_value"
                    parameterAst = QueryParameterAst<Any?>().apply {
                        metadata.name = "nullable_value"
                        metadata.value = null
                        metadata.sqlType = Types.INTEGER
                    }
                }
            }
        }

        fixture.executor.execute(nullableInsert, {})

        val nullableBound = assertIs<QueryExecutionStatement.Bound>(fixture.scope.statements.single()).query
        assertEquals(1, nullableBound.parameterOccurrences.size)
        assertEquals(null, nullableBound.parameterOccurrences.single().value)
        assertEquals(Types.INTEGER, nullableBound.parameterOccurrences.single().sqlType)

        fixture.scope.statements.clear()
        fixture.executor.queryBuilder(selectWithConditions("same", "same")).execute()
        val repeated = assertIs<QueryExecutionStatement.Bound>(fixture.scope.statements.single()).query
        assertEquals(listOf("same", "same"), repeated.parameterOccurrences.map { it.name })
        assertEquals(listOf(7, 7), repeated.parameterOccurrences.map { it.value })
    }

    @Test
    fun `existing CTE execute API uses the Scope-bound SELECT path and maps synchronously`() {
        val fixture = fixture()
        fixture.scope.rows = listOf(mapOf("value" to 42), mapOf("value" to 42))

        val result: ExecuteResult<ICollection<ScopeTestCte>> = ScopeTestCte().execute(fixture.executor, Unit)

        val values = assertIs<ExecuteResult.Success<ICollection<ScopeTestCte>>>(result).result!!
        assertEquals(listOf<Int?>(42, 42), buildList { values.forEach { add(it.value) } })
        assertEquals(1, fixture.scope.selectCalls)
        assertIs<QueryExecutionStatement.Bound>(fixture.scope.statements.single())
        assertEquals(0, fixture.provider.buildCalls)
    }

    @Test
    fun `SQL and row mapping failures are propagated through Operation scope`() {
        val fixture = fixture()
        val sqlFailure = IllegalStateException("scope SQL failed")
        fixture.scope.nextSelectFailure = sqlFailure

        val failedSql = fixture.executor.queryBuilder(selectQuery()).execute()

        assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(failedSql).exception)
        assertSame(sqlFailure, fixture.scope.failures.single())

        fixture.scope.failures.clear()
        fixture.scope.statements.clear()
        fixture.scope.rows = listOf(mapOf("wrong_alias" to 1))
        val failedMapping = fixture.executor.queryBuilder(selectQuery()).execute()

        val mappingFailure = assertIs<ExecuteResult.Failure>(failedMapping).exception
        assertTrue(mappingFailure.message.orEmpty().contains("column label"))
        assertSame(mappingFailure, fixture.scope.failures.single())
        assertEquals(0, fixture.provider.buildCalls)
    }

    @Test
    fun `scope-bound lifecycle and executeOut fail closed without acquiring resources`() {
        val fixture = fixture()
        var connectionCallbackCalled = false
        var transactionCallbackCalled = false

        val connection = fixture.executor.connection {
            connectionCallbackCalled = true
            ExecuteResult.Success(true)
        }
        val transaction = fixture.executor.transaction {
            transactionCallbackCalled = true
            ExecuteResult.Success(true)
        }
        val executeOutFailure = runCatching {
            fixture.executor.executeOut(selectQuery()) { _, _ -> error("executeOut must not execute") }
        }.exceptionOrNull()
        var rawSelectResult: ExecuteResult<java.sql.ResultSet>? = null
        fixture.executor.execute(selectQuery(), { rawSelectResult = it })

        assertIs<ExecuteResult.Failure>(connection)
        assertIs<ExecuteResult.Failure>(transaction)
        assertIs<UnsupportedOperationException>(executeOutFailure)
        assertIs<UnsupportedOperationException>(assertIs<ExecuteResult.Failure>(rawSelectResult).exception)
        assertTrue(!connectionCallbackCalled)
        assertTrue(!transactionCallbackCalled)
        assertEquals(4, fixture.scope.failures.size)
        assertEquals(0, fixture.provider.buildCalls)
        assertEquals(0, fixture.scope.selectCalls)
        assertEquals(0, fixture.scope.insertCalls + fixture.scope.updateCalls + fixture.scope.deleteCalls)
    }

    @Test
    fun `callback and fluent mapper failures reach the Scope failure recorder`() {
        val fixture = fixture()
        val callbackFailure = IllegalArgumentException("consumer callback failed")

        val callbackThrown = runCatching {
            fixture.executor.get(selectQuery(), blockExecute = { throw callbackFailure })
        }.exceptionOrNull()
        assertSame(callbackFailure, callbackThrown)
        assertSame(callbackFailure, fixture.scope.failures.single())

        fixture.scope.failures.clear()
        fixture.scope.statements.clear()
        fixture.scope.rows = listOf(mapOf("value" to 1))
        val mapped = fixture.executor.queryBuilder(selectQuery()).executeAs<Int> { error("fluent mapper failed") }

        val mappingFailure = assertIs<ExecuteResult.Failure>(mapped).exception
        assertTrue(mappingFailure.message.orEmpty().contains("Failed to map SELECT result row"))
        assertSame(mappingFailure, fixture.scope.failures.single())
        assertEquals(0, fixture.provider.buildCalls)
    }

    private fun fixture(): Fixture {
        val provider = CountingConnectionProvider()
        val databaseContext = DatabaseContext(
            name = "scope-bound-test",
            config = DatabaseConfig(
                connection = DatabaseConnectionConfig("jdbc:unused", null, "unused", "unused", "unused"),
                platform = DatabasePlatformConfig(DialectQuery.MARIA_DB),
            ),
            connectionProvider = provider,
        )
        val scope = RecordingOperationContext()
        return Fixture(databaseContext, provider, scope, QueryBuilderExecutor.scopeBound(databaseContext, scope))
    }

    private fun selectQuery() = QueryRenderSelectBuilder().apply {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias("value")
                execute(DataType.INT)
            }
        }
    }

    private fun selectWithConditions(
        firstName: String,
        secondName: String,
        firstValue: Any? = 7,
        secondValue: Any? = 7,
    ) = QueryRenderSelectBuilder().apply {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias("value")
                execute(DataType.INT)
            }
        }
        from { table("records").alias("r") }
        where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("1") }
                    operationEqual()
                    sideValueParam { paramName(firstName); paramValue(firstValue) }
                }
                addCondition {
                    logicalAnd()
                    sideSelector { tableAttribute("1") }
                    operationEqual()
                    sideValueParam { paramName(secondName); paramValue(secondValue) }
                }
            }
        }
    }

    private fun insertQuery() = QueryRenderInsertBuilder()
        .table { table("records") }
        .addValue {
            column("value")
            setValue { paramName("insert_value"); paramValue(10) }
        }

    private fun updateQuery() = QueryRenderUpdateBuilder()
        .table { table("records") }
        .addValue {
            column("value")
            setValue { paramName("update_value"); paramValue(10) }
        }

    private fun deleteQuery() = QueryRenderDeleteBuilder()
        .table { table("records") }
        .where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("id") }
                    operationEqual()
                    sideValueParam { paramName("delete_id"); paramValue(1) }
                }
            }
        }

    private data class Fixture(
        val context: DatabaseContext,
        val provider: CountingConnectionProvider,
        val scope: RecordingOperationContext,
        val executor: QueryBuilderExecutor,
    )

    private class CountingConnectionProvider : IDatabaseConnection {
        var buildCalls: Int = 0
            private set

        override fun build(): Connection {
            buildCalls++
            error("Scope-bound Query execution must not acquire a JDBC Connection")
        }
    }

    private class RecordingOperationContext : OperationExecutionContext {
        override val databasePlatformConfig = DatabasePlatformConfig(DialectQuery.MY_SQL)
        val statements = mutableListOf<QueryExecutionStatement>()
        val failures = mutableListOf<Throwable>()
        var rows: List<Map<String, Any?>> = emptyList()
        var nextSelectFailure: Throwable? = null
        var selectCalls = 0
        var insertCalls = 0
        var updateCalls = 0
        var deleteCalls = 0
        var nextSelectError: Error? = null
        var nextUpdateFailure: Throwable? = null

        override fun executeDdl(sql: String, parameters: List<OperationParameter>): ExecuteResult<Boolean> =
            error("Scope-bound Query executor must not execute DDL")

        override fun executeDdlWithPolicy(
            sql: String,
            parameters: List<OperationParameter>,
            failurePolicy: DdlFailurePolicy,
        ): DdlExecutionOutcome = error("Scope-bound Query executor must not execute DDL")

        override fun <T> executeRead(
            sql: String,
            parameters: List<OperationParameter>,
            read: (OperationQueryRows) -> T,
        ): ExecuteResult<T> = error("Scope-bound Query executor must use typed SELECT")

        override fun <T> executeSelect(query: BuiltQuery, read: (OperationQueryRows) -> T): ExecuteResult<T> {
            statements += QueryExecutionStatement.Built(query)
            return runSelect(read)
        }

        override fun <T> executeSelect(query: BoundQuery, read: (OperationQueryRows) -> T): ExecuteResult<T> {
            statements += QueryExecutionStatement.Bound(query)
            return runSelect(read)
        }

        override fun executeInsert(query: BuiltQuery): ExecuteResult<Long> {
            statements += QueryExecutionStatement.Built(query)
            insertCalls++
            return ExecuteResult.Success(901L)
        }

        override fun executeInsert(query: BoundQuery): ExecuteResult<Long> {
            statements += QueryExecutionStatement.Bound(query)
            insertCalls++
            return ExecuteResult.Success(901L)
        }

        override fun executeUpdate(query: BuiltQuery): ExecuteResult<Int> {
            statements += QueryExecutionStatement.Built(query)
            updateCalls++
            nextUpdateFailure?.let { failure ->
                nextUpdateFailure = null
                throw failure
            }
            return ExecuteResult.Success(2)
        }

        override fun executeUpdate(query: BoundQuery): ExecuteResult<Int> {
            statements += QueryExecutionStatement.Bound(query)
            updateCalls++
            nextUpdateFailure?.let { failure ->
                nextUpdateFailure = null
                throw failure
            }
            return ExecuteResult.Success(2)
        }

        override fun executeDelete(query: BuiltQuery): ExecuteResult<Int> {
            statements += QueryExecutionStatement.Built(query)
            deleteCalls++
            return ExecuteResult.Success(3)
        }

        override fun executeDelete(query: BoundQuery): ExecuteResult<Int> {
            statements += QueryExecutionStatement.Bound(query)
            deleteCalls++
            return ExecuteResult.Success(3)
        }

        override fun <T> failed(failure: Throwable): ExecuteResult<T> {
            failures += failure
            return ExecuteResult.Failure(failure)
        }

        private fun <T> runSelect(read: (OperationQueryRows) -> T): ExecuteResult<T> {
            selectCalls++
            nextSelectError?.let { error ->
                nextSelectError = null
                throw error
            }
            nextSelectFailure?.let { failure ->
                nextSelectFailure = null
                return failed(failure)
            }
            return try {
                ExecuteResult.Success(read(RecordingRows(rows)))
            } catch (failure: Throwable) {
                failed<T>(failure)
            }
        }
    }

    private class RecordingRows(private val rows: List<Map<String, Any?>>) : OperationQueryRows {
        override val columnLabels: List<String> = rows.firstOrNull()?.keys?.toList() ?: listOf("value")
        private var index = -1

        override fun next(): Boolean {
            index++
            return index < rows.size
        }

        override fun getString(columnLabel: String): String? = rows[index][columnLabel] as? String
        override fun getInt(columnLabel: String): Int? = (rows[index][columnLabel] as? Number)?.toInt()
        override fun getLong(columnLabel: String): Long? = (rows[index][columnLabel] as? Number)?.toLong()
        override fun getBoolean(columnLabel: String): Boolean? = rows[index][columnLabel] as? Boolean
        override fun getDouble(columnLabel: String): Double? = (rows[index][columnLabel] as? Number)?.toDouble()
        override fun getBigDecimal(columnLabel: String): java.math.BigDecimal? =
            rows[index][columnLabel] as? java.math.BigDecimal

        override fun getBytes(columnLabel: String): ByteArray? = rows[index][columnLabel] as? ByteArray
    }

    @QBQuery(name = "scope_bound_test")
    private class ScopeTestCte : CteDeclaration<Unit, ScopeTestCte>() {
        @QBQuerySelect(name = "value", alias = "value")
        var value: Int? = null

        override fun fromQueryRow(row: QueryRow): ScopeTestCte = super.fromQueryRow(row)

        override fun createCte(params: Unit): Cte = buildCte {
            from { table("records").alias("r") }
            select {
                addColumn {
                    column { tableAttribute("1") }
                    alias(ScopeTestCte::value)
                    execute(DataType.INT)
                }
            }
            where {
                conditions {
                    addCondition {
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValueParam { paramName("cte_filter"); paramValue(1) }
                    }
                }
            }
        }
    }
}
