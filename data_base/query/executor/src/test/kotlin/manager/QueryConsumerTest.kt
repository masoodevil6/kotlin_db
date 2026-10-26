package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadata
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadataProvider
import gog.my_project.data_base.query.api.metadata.EffectiveRelationMetadata
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import java.sql.ResultSet
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@QBQuery(name = "details_query", alias = "details_source")
private class DetailsQueryDeclaration : CteDeclaration<Unit, DetailsQueryDeclaration>() {
    override fun fromQueryRow(row: QueryRow): DetailsQueryDeclaration =
        error("Details-only fixture uses raw row execution")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias("value")
            }
        }
    }
}

@QBQuery(name = "typed_details_query", alias = "typed_details_source")
private class TypedDetailsQueryDeclaration : CteDeclaration<Unit, TypedDetailsQueryDeclaration>() {
    override fun fromQueryRow(row: QueryRow): TypedDetailsQueryDeclaration =
        error("Details-only fixture uses raw row execution")

    @gog.my_project.data_base.core.annotations.models.QBQuerySelect(
        name = "logical_value",
        alias = "typed_value",
    )
    val value: Long
        get() = error("Metadata extraction must not invoke output getters")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(TypedDetailsQueryDeclaration::value)
                execute(DataType.LONG)
            }
        }
    }
}

class QueryConsumerTest {

    @Test
    fun `queryBuilder configures one builder and binds the receiving executor`() {
        val row = QueryRow(listOf("user_id" to 17))
        val executor = RecordingExecutor(firstResult = ExecuteResult.Success(row))
        var callbackQuery: IQueryRenderSelectApi? = null

        val consumer = executor.queryBuilder { operations ->
            operations.querySelect { query ->
                callbackQuery = query
                query
                    .select {
                        addColumn {
                            column { tableColumn("users", "id") }
                            alias("user_id")
                        }
                    }
                    .from { table("users").alias("u") }
                    .where { }
            }
        }

        assertIs<ExecuteResult.Success<QueryRow>>(consumer.first())
        assertSame(callbackQuery, executor.firstQuery)
        assertEquals(1, executor.firstCalls)
        assertTrue(callbackQuery?.ast?.select != null)
        assertTrue(callbackQuery?.ast?.table != null)
        assertTrue(callbackQuery?.ast?.where != null)
    }

    @Test
    fun `consumer delegates execution and renders SQL from its stored query`() {
        val executor = RecordingExecutor(
            getResult = ExecuteResult.Success(emptyList()),
            sqlResult = "SELECT users.id AS user_id",
        )
        var callbackQuery: IQueryRenderSelectApi? = null
        val consumer = executor.queryBuilder { operations ->
            operations.querySelect { query ->
                callbackQuery = query
                query.select {
                    addColumn {
                        column { tableColumn("users", "id") }
                        alias("user_id")
                    }
                }
            }
        }

        assertEquals(emptyList(), assertIs<ExecuteResult.Success<List<QueryRow>>>(consumer.execute()).result)
        val builtQuery = consumer.sql()
        assertEquals(DialectSelector().select(DefaultDatabaseConfig.config.dialect).render(callbackQuery!!.ast), builtQuery.query)
        assertSame(callbackQuery, executor.getQuery)
        assertNull(executor.sqlQuery)
        assertEquals(1, executor.getCalls)
        assertEquals(0, executor.sqlCalls)
    }

    @Test
    fun `sql callback receives a single AST parameter with its SQL type`() {
        val consumer = RecordingExecutor(renderQuery = true).queryBuilderSelect { query ->
            query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
                .from { table("users").alias("u") }
                .where {
                    conditions {
                        addCondition {
                            sideSelector { tableColumn("u", "id") }
                            operationEqual()
                            sideValueParam {
                                paramName("id1")
                                paramValue(1)
                            }
                        }
                    }
                }
        }

        var callbackSql: String? = null
        var callbackParams: List<IQueryParameterBinding> = emptyList()
        consumer.sql { (queryText, params) ->
            callbackSql = queryText
            callbackParams = params
        }

        assertTrue(callbackSql.orEmpty().contains(":id1"), callbackSql)
        assertEquals(listOf("id1"), callbackParams.map { it.name })
        assertEquals(listOf(1), callbackParams.map { it.value })
        assertEquals(listOf(Types.INTEGER), callbackParams.map { it.sqlType })
    }

    @Test
    fun `sql callback parameters follow rendered SQL order for multiple AST definitions`() {
        val consumer = RecordingExecutor(renderQuery = true).queryBuilderSelect { query ->
            query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
                .from { table("users").alias("u") }
                .where {
                    conditions {
                        addCondition {
                            sideSelector { tableColumn("u", "id") }
                            operationEqual()
                            sideValueParam {
                                paramName("first_id")
                                paramValue(1)
                            }
                        }
                        addCondition {
                            logicalAnd()
                            sideSelector { tableColumn("u", "id") }
                            operationGreaterThan()
                            sideValueParam {
                                paramName("second_id")
                                paramValue(2L)
                            }
                        }
                    }
                }
        }

        var callbackSql: String? = null
        var callbackParams: List<IQueryParameterBinding> = emptyList()
        consumer.sql { (queryText, params) ->
            callbackSql = queryText
            callbackParams = params
        }

        val sql = callbackSql.orEmpty()
        assertTrue(sql.indexOf(":first_id") < sql.indexOf(":second_id"), sql)
        assertEquals(listOf("first_id", "second_id"), callbackParams.map { it.name })
        val values = callbackParams.map { it.value }
        assertEquals(2, values.size)
        assertEquals(1, values[0])
        assertEquals(2L, values[1])
        assertEquals(listOf(Types.INTEGER, Types.BIGINT), callbackParams.map { it.sqlType })
    }

    @Test
    fun `sql callback records every use of the same parameter AST`() {
        var queryBuilder: IQueryRenderSelectApi? = null
        val consumer = RecordingExecutor(renderQuery = true).queryBuilderSelect { query ->
            queryBuilder = query
            query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
                .from { table("users").alias("u") }
                .where {
                    conditions {
                        repeat(2) { index ->
                            addCondition {
                                sideSelector { tableColumn("u", "id") }
                                operationEqual()
                                sideValueParam {
                                    paramName("shared_id_$index")
                                    paramValue(index + 1)
                                }
                            }
                        }
                    }
                }
        }

        val conditionNodes = requireNotNull(queryBuilder).ast.where!!.condition!!.conditions
            .map { it as IQueryConditionsAst }
        conditionNodes[1].conditionSideRight = conditionNodes[0].conditionSideRight

        var callbackSql: String? = null
        var callbackParams: List<IQueryParameterBinding> = emptyList()
        consumer.sql { (queryText, params) ->
            callbackSql = queryText
            callbackParams = params
        }

        assertEquals(2, callbackSql.orEmpty().windowed(":shared_id_0".length)
            .count { it == ":shared_id_0" })
        assertEquals(listOf("shared_id_0", "shared_id_0"), callbackParams.map { it.name })
        assertEquals(listOf(1, 1), callbackParams.map { it.value })
        assertSame(callbackParams[0], callbackParams[1])
    }

    @Test
    fun `legacy sideValueCollection remains available through the SQL callback`() {
        val consumer = RecordingExecutor(renderQuery = true).queryBuilderSelect { query ->
            query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
                .from { table("users").alias("u") }
                .where {
                    conditions {
                        addCondition {
                            sideSelector { tableColumn("u", "id") }
                            operationIn()
                            sideValueCollection {
                                addParam {
                                    paramName("userId0")
                                    paramValue(11)
                                }
                                addParam {
                                    paramName("userId1")
                                    paramValue(22)
                                }
                            }
                        }
                    }
                }
        }

        var callbackSql: String? = null
        var callbackParams: List<IQueryParameterBinding> = emptyList()
        consumer.sql { (queryText, params) ->
            callbackSql = queryText
            callbackParams = params
        }

        assertTrue(callbackSql.orEmpty().contains(":userId0"), callbackSql)
        assertTrue(callbackSql.orEmpty().contains(":userId1"), callbackSql)
        assertEquals(listOf("userId0", "userId1"), callbackParams.map { it.name })
        assertEquals(listOf(11, 22), callbackParams.map { it.value })
    }

    @Test
    fun `details reads effective metadata without rendering SQL and keeps the consumer chain`() {
        val executor = RecordingExecutor(sqlResult = "SELECT scripted")
        val detailsRelation = DetailsQueryDeclaration().createCte(Unit)
        val consumer = executor.queryBuilderSelect { query ->
            query
                .withs { addWith { with(detailsRelation) } }
                .from { cte(detailsRelation.name).alias("details_occurrence") }
                .select {
                    addColumn {
                        column { tableAttribute("1") }
                        alias("result")
                        execute(DataType.LONG)
                    }
                }
        }

        val metadata = consumer.details()

        assertEquals(2, metadata.relations.size)
        val withDefinition = metadata.relations.single {
            it.withSqlName == "details_query" && it.occurrenceAlias == null
        }
        assertEquals("details_query", withDefinition.logicalQueryName)
        assertEquals("details_query", withDefinition.cteRuntimeName)
        assertEquals(null, withDefinition.occurrenceAlias)
        assertEquals(listOf("value"), withDefinition.outputs.map { it.publishedSqlAlias })
        val sourceOccurrence = metadata.relations.single { it.occurrenceAlias == "details_occurrence" }
        assertEquals("details_query", sourceOccurrence.logicalQueryName)
        assertEquals("details_query", sourceOccurrence.cteRuntimeName)
        assertEquals("details_query", sourceOccurrence.withSqlName)
        assertEquals(listOf("value"), sourceOccurrence.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf("result"), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG), metadata.outputs.map { it.executionType })
        assertEquals(0, executor.sqlCalls)
        assertEquals(0, executor.firstCalls)
        assertEquals(0, executor.getCalls)

        assertSame(consumer, consumer.details { observed -> assertEquals(metadata, observed) })
    }

    @Test
    fun `details analyzes a custom select without a metadata provider while SQL remains available`() {
        val executor = RecordingExecutor(sqlResult = "SELECT scripted")
        val delegate = QueryRenderSelectBuilder()
        val customQuery = object : IQueryRenderSelectApi by delegate {}
        val consumer = executor.queryBuilder(customQuery)

        assertEquals(EffectiveQueryMetadata(relations = emptyList()), consumer.details())
        assertEquals(DialectSelector().select(DefaultDatabaseConfig.config.dialect).render(customQuery.ast), consumer.sql().query)
        assertEquals(0, executor.sqlCalls)
    }

    @Test
    fun `details preserves a custom metadata provider`() {
        val executor = RecordingExecutor()
        val delegate = QueryRenderSelectBuilder()
        val providedMetadata = EffectiveQueryMetadata(
            relations = listOf(
                EffectiveRelationMetadata(
                    logicalQueryName = "custom_logical_query",
                    cteRuntimeName = "custom_runtime_cte",
                    withSqlName = null,
                    occurrenceAlias = null,
                    outputs = emptyList(),
                ),
            ),
        )
        val customQuery = object : IQueryRenderSelectApi by delegate, EffectiveQueryMetadataProvider {
            override fun effectiveQueryMetadata(): EffectiveQueryMetadata = providedMetadata
        }

        assertEquals(providedMetadata, executor.queryBuilder(customQuery).details())
    }

    @Test
    fun `details remains available when an existing builder is bound to the executor`() {
        val executor = RecordingExecutor()
        val detailsRelation = DetailsQueryDeclaration().createCte(Unit)
        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(detailsRelation) } }
            .from { cte(detailsRelation.name).alias("existing_occurrence") }

        val consumer = executor.queryBuilder(query)
        val metadata = consumer.details()

        assertEquals(2, metadata.relations.size)
        val withDefinition = metadata.relations.single {
            it.withSqlName == "details_query" && it.occurrenceAlias == null
        }
        assertEquals("details_query", withDefinition.logicalQueryName)
        assertEquals("details_query", withDefinition.cteRuntimeName)
        assertEquals(null, withDefinition.occurrenceAlias)
        assertEquals(listOf("value"), withDefinition.outputs.map { it.publishedSqlAlias })
        val sourceOccurrence = metadata.relations.single { it.occurrenceAlias == "existing_occurrence" }
        assertEquals("details_query", sourceOccurrence.logicalQueryName)
        assertEquals("details_query", sourceOccurrence.cteRuntimeName)
        assertEquals("details_query", sourceOccurrence.withSqlName)
        assertEquals(listOf("value"), sourceOccurrence.outputs.map { it.publishedSqlAlias })
        assertEquals(0, executor.sqlCalls)
        assertEquals(0, executor.firstCalls)
        assertEquals(0, executor.getCalls)
    }

    @Test
    fun `details returns the same metadata before and after SQL inspection`() {
        val executor = RecordingExecutor(sqlResult = "SELECT scripted")
        val detailsRelation = DetailsQueryDeclaration().createCte(Unit)
        var queryReference: IQueryRenderSelectApi? = null
        val consumer = executor.queryBuilderSelect { query ->
            queryReference = query
            query.withs { addWith { with(detailsRelation) } }
            query.from { cte(detailsRelation.name).alias("snapshot_occurrence") }
        }

        val beforeSql = consumer.details()
        assertEquals(0, executor.sqlCalls)

        val sql = consumer.sql()
        assertEquals(DialectSelector().select(DefaultDatabaseConfig.config.dialect).render(queryReference!!.ast), sql.query)
        assertTrue(sql.query.orEmpty().contains("snapshot_occurrence"), sql.query)
        assertEquals(0, executor.sqlCalls)

        val afterSql = consumer.details()
        assertEquals(beforeSql, afterSql)
        assertEquals(0, executor.sqlCalls)
    }

    @Test
    fun `details sql and execute preserve metadata SQL and parameters in one flow`() {
        val executor = RecordingExecutor(
            getResult = ExecuteResult.Success(listOf(QueryRow(listOf("result" to 7)))),
            sqlResult = "SELECT :flow_value",
        )
        val detailsRelation = DetailsQueryDeclaration().createCte(Unit)
        val consumer = executor.queryBuilderSelect { query ->
            query
                .withs { addWith { with(detailsRelation) } }
                .from { cte(detailsRelation.name).alias("flow_source") }
                .where {
                    conditions {
                        addCondition {
                            sideSelector { tableAttribute("1") }
                            operationEqual()
                            sideValueParam {
                                paramName("flow_value")
                                paramValue(7)
                            }
                        }
                    }
                }
        }

        val parametersBeforeDetails = listOf("flow_value" to 7)
        val metadata = consumer.details()

        assertEquals(0, executor.sqlCalls)
        assertEquals(2, metadata.relations.size)
        val withDefinition = metadata.relations.single {
            it.withSqlName == "details_query" && it.occurrenceAlias == null
        }
        assertEquals("details_query", withDefinition.logicalQueryName)
        assertEquals("details_query", withDefinition.cteRuntimeName)
        assertEquals(null, withDefinition.occurrenceAlias)
        assertEquals(listOf("value"), withDefinition.outputs.map { it.publishedSqlAlias })
        val sourceOccurrence = metadata.relations.single { it.occurrenceAlias == "flow_source" }
        assertEquals("details_query", sourceOccurrence.logicalQueryName)
        assertEquals("details_query", sourceOccurrence.cteRuntimeName)
        assertEquals("details_query", sourceOccurrence.withSqlName)
        assertEquals(listOf("value"), sourceOccurrence.outputs.map { it.publishedSqlAlias })

        val built = consumer.sql()
        assertTrue(requireNotNull(built.query).contains(":flow_value"), built.query)
        assertEquals(parametersBeforeDetails, built.params.map { it.name to it.value })
        assertIs<QueryParameterBinding>(built.params.single())
        val execution = consumer.execute()
        assertEquals(1, executor.getCalls)
        val result = assertIs<ExecuteResult.Success<List<QueryRow>>>(execution).result.orEmpty()
        assertEquals(7, result.single()["result"])
    }

    @Test
    fun `details does not change typed execution metadata or returned result`() {
        val executor = RecordingExecutor(
            firstResult = ExecuteResult.Success(QueryRow(listOf("typed_value" to 11L))),
        )
        val typedDetailsRelation = TypedDetailsQueryDeclaration().createCte(Unit)
        val consumer = executor.queryBuilderSelect { query ->
            query
                .withs { addWith { with(typedDetailsRelation) } }
                .from { cte(typedDetailsRelation.name).alias("typed_source") }
        }

        val metadata = consumer.details()
        assertEquals(2, metadata.relations.size)
        val withDefinition = metadata.relations.single {
            it.withSqlName == "typed_details_query" && it.occurrenceAlias == null
        }
        assertEquals("typed_details_query", withDefinition.logicalQueryName)
        assertEquals("typed_details_query", withDefinition.cteRuntimeName)
        assertEquals(null, withDefinition.occurrenceAlias)
        assertEquals(DataType.LONG, withDefinition.outputs.single().executionType)
        assertEquals("typed_value", withDefinition.outputs.single().publishedSqlAlias)
        val sourceOccurrence = metadata.relations.single { it.occurrenceAlias == "typed_source" }
        assertEquals("typed_details_query", sourceOccurrence.logicalQueryName)
        assertEquals("typed_details_query", sourceOccurrence.cteRuntimeName)
        assertEquals("typed_details_query", sourceOccurrence.withSqlName)
        assertEquals(DataType.LONG, sourceOccurrence.outputs.single().executionType)
        assertEquals("typed_value", sourceOccurrence.outputs.single().publishedSqlAlias)

        val result = assertIs<ExecuteResult.Success<QueryRow>>(consumer.first()).result
        assertEquals(11L, result?.get("typed_value"))
        val executedWithDefinition = requireNotNull(executor.firstQuery?.ast?.withs)
            .withs
            .single { it.metadata.withName == "typed_details_query" }
        val executedDefinitionSelect = requireNotNull(requireNotNull(executedWithDefinition.withBody).select)
        assertEquals(DataType.LONG, executedDefinitionSelect.columns.single().metadata.ExecutionType)
        assertEquals(1, executor.firstCalls)
    }

    @Test
    fun `details observes query state at call time after SQL inspection`() {
        val executor = RecordingExecutor(sqlResult = "SELECT before")
        val detailsRelation = DetailsQueryDeclaration().createCte(Unit)
        var queryReference: IQueryRenderSelectApi? = null
        val consumer = executor.queryBuilderSelect { query ->
            queryReference = query
            query
        }

        assertEquals(
            DialectSelector().select(DefaultDatabaseConfig.config.dialect).render(queryReference!!.ast),
            consumer.sql().query,
        )
        queryReference!!.withs { addWith { with(detailsRelation) } }
        queryReference!!.from { cte(detailsRelation.name).alias("changed_source") }

        val afterChange = consumer.details()
        assertEquals(2, afterChange.relations.size)
        val withDefinition = afterChange.relations.single {
            it.withSqlName == "details_query" && it.occurrenceAlias == null
        }
        assertEquals("details_query", withDefinition.logicalQueryName)
        assertEquals("details_query", withDefinition.cteRuntimeName)
        assertEquals(null, withDefinition.occurrenceAlias)
        assertEquals(listOf("value"), withDefinition.outputs.map { it.publishedSqlAlias })
        val sourceOccurrence = afterChange.relations.single { it.occurrenceAlias == "changed_source" }
        assertEquals("details_query", sourceOccurrence.logicalQueryName)
        assertEquals("details_query", sourceOccurrence.cteRuntimeName)
        assertEquals("details_query", sourceOccurrence.withSqlName)
        assertEquals(listOf("value"), sourceOccurrence.outputs.map { it.publishedSqlAlias })
        assertEquals(0, executor.sqlCalls)
    }

    @Test
    fun `success transforms value and supports inferred nullable result type`() {
        val row = QueryRow(listOf("user_id" to 17))
        val transformed: ExecuteResult<Any?> = ExecuteResult.Success(row).success { it["user_id"] }

        assertEquals(17, assertIs<ExecuteResult.Success<Any?>>(transformed).result)

        var transformCalled = false
        val noRow: ExecuteResult<String> = ExecuteResult.Success<QueryRow>(null).success {
            transformCalled = true
            it["user_id"].toString()
        }
        assertFalse(transformCalled)
        assertNull(assertIs<ExecuteResult.Success<String>>(noRow).result)
    }

    @Test
    fun `success preserves failure and propagates transformer exception`() {
        val failure = IllegalArgumentException("query failed")
        val preserved = ExecuteResult.Failure(failure).success { it.toString() }
        assertSame(failure, assertIs<ExecuteResult.Failure>(preserved).exception)

        val transformFailure = IllegalStateException("transform failed")
        val actual = assertFailsWith<IllegalStateException> {
            ExecuteResult.Success("value").success<String, String> { throw transformFailure }
        }
        assertSame(transformFailure, actual)
    }

    @Test
    fun `error observes only failure and preserves result for chaining`() {
        val failure = IllegalArgumentException("query failed")
        var observed: Throwable? = null
        val original: ExecuteResult<String> = ExecuteResult.Failure(failure)

        val returned = original.error { observed = it }

        assertSame(failure, observed)
        assertSame(original, returned)

        var successHandlerCalled = false
        val success: ExecuteResult<String> = ExecuteResult.Success("ok")
        assertSame(success, success.error { successHandlerCalled = true })
        assertFalse(successHandlerCalled)
    }

    @Test
    fun `error handler exception propagates`() {
        val handlerFailure = IllegalStateException("handler failed")
        val actual = assertFailsWith<IllegalStateException> {
            ExecuteResult.Failure(RuntimeException("query failed")).error { throw handlerFailure }
        }
        assertSame(handlerFailure, actual)
    }

    @Test
    fun `vision fluent chain transforms and observes the same result`() {
        val executor = RecordingExecutor(
            firstResult = ExecuteResult.Success(QueryRow(listOf("user_id" to 17))),
        )
        var observedError: Throwable? = null

        val result: ExecuteResult<Any?> = executor
            .queryBuilder { operations ->
                operations.querySelect { query ->
                    query
                        .select {
                            addColumn {
                                column { tableColumn("users", "id") }
                                alias("user_id")
                            }
                        }
                        .from { table("users").alias("u") }
                        .where { }
                }
            }
            .first()
            .success { row -> row["user_id"] }
            .error { error -> observedError = error }

        assertEquals(17, assertIs<ExecuteResult.Success<Any?>>(result).result)
        assertNull(observedError)
        assertEquals(1, executor.firstCalls)
    }

    private class RecordingExecutor(
        private val firstResult: ExecuteResult<QueryRow> = ExecuteResult.Success(null),
        private val getResult: ExecuteResult<List<QueryRow>> = ExecuteResult.Success(emptyList()),
        private val sqlResult: String = "SELECT scripted",
        private val renderQuery: Boolean = false,
    ) : IQueryBuilderExecutor {
        var firstCalls = 0
        var getCalls = 0
        var sqlCalls = 0
        var firstQuery: IQueryRenderSelectApi? = null
        var getQuery: IQueryRenderSelectApi? = null
        var sqlQuery: IQueryRenderSelectApi? = null

        override fun first(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<QueryRow>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            firstCalls++
            firstQuery = queryBuilder
            blockExecute(firstResult)
        }

        override fun get(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<List<QueryRow>>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            getCalls++
            getQuery = queryBuilder
            blockExecute(getResult)
        }

        override fun sql(queryBuilder: IQueryRenderSelectApi): String {
            sqlCalls++
            sqlQuery = queryBuilder
            return if (renderQuery) {
                DialectSelector().select(DefaultDatabaseConfig.config.dialect).render(queryBuilder.ast)
                    ?: error("rendered sql is null")
            } else {
                sqlResult
            }
        }

        override fun execute(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<ResultSet>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("The Smart SELECT methods are overridden by this recording executor")

        override fun execute(
            queryBuilder: IQueryRenderInsertApi,
            blockExecute: (ExecuteResult<Long>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used in this test")

        override fun execute(
            queryBuilder: IQueryRenderUpdateApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used in this test")

        override fun execute(
            queryBuilder: IQueryRenderDeleteApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used in this test")
    }
}
