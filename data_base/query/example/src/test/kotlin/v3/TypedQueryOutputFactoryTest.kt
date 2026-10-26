package gog.my_project.data_base.query.example.v3

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.cte.mapper.CteQueryRowMapper
import gog.my_project.data_base.query.example.v3.queries.relations.UserDisplayQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPageQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPhoneQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsQueryV3
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserDisplayQueryV31
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserPageQueryV31
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserPhoneQueryV31
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserStatsQueryV31
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryRow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TypedQueryOutputFactoryTest {
    @Test
    fun `metadata mapper maps published aliases into independent query instances`() {
        val executor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "fullName", "age"),
                rows = listOf(
                    listOf(1, "Ada Lovelace", 36),
                    listOf(2, "Grace Hopper", null),
                ),
            ),
        )
        val declaration = UserDisplayQueryV3().apply {
            id = 99
            fullName = "query declaration state"
            age = 88
        }
        val rows = materializedRows(executor)
        assertEquals(1, executor.selectExecutions)

        val first = declaration.fromQueryRow(rows[0])
        val second = declaration.fromQueryRow(rows[1])

        assertNotSame(declaration, first)
        assertNotSame(first, second)
        assertEquals(99, declaration.id)
        assertEquals("query declaration state", declaration.fullName)
        assertEquals(88, declaration.age)
        assertEquals(1, first.id)
        assertEquals("Ada Lovelace", first.fullName)
        assertEquals(36, first.age)
        assertEquals(2, second.id)
        assertEquals("Grace Hopper", second.fullName)
        assertEquals(null, second.age)
        assertEquals(1, executor.selectExecutions)
        assertFailsWith<IllegalArgumentException> { rows[0].getValue("full_name") }
    }

    @Test
    fun `metadata mapper rejects values that do not match declared output type`() {
        val executor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "fullName", "age"),
                rows = listOf(listOf(1, "Ada Lovelace", 36)),
            ),
        )
        val row = materializedRows(executor).single()
        row["age"] = "36"

        val failure = assertFailsWith<IllegalArgumentException> {
            CteQueryRowMapper.map(UserDisplayQueryV3::class, row)
        }

        assertTrue(failure.message.orEmpty().contains("age"))
        assertTrue(failure.message.orEmpty().contains("INT or NULL"))
        assertEquals(1, executor.selectExecutions)
    }

    @Test
    fun `metadata mapper rejects null for a non-null id output`() {
        val executor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "fullName", "age"),
                rows = listOf(listOf(null, "Ada Lovelace", 36)),
            ),
        )
        val row = materializedRows(executor, displayOutputs()).single()

        val failure = assertFailsWith<IllegalArgumentException> {
            CteQueryRowMapper.map(UserDisplayQueryV3::class, row)
        }

        assertTrue(failure.message.orEmpty().contains("id"))
        assertTrue(failure.message.orEmpty().contains("non-null INT"))
        assertEquals(1, executor.selectExecutions)
    }

    @Test
    fun `metadata mapper maps stats published aliases`() {
        val executor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "name", "family", "age"),
                rows = listOf(listOf(11, "Ada", "Lovelace", 36)),
            ),
        )

        val output = CteQueryRowMapper.map(
            UserStatsQueryV3::class,
            materializedRows(executor, statsOutputs()).single(),
        )

        assertEquals(11, output.id)
        assertEquals("Ada", output.name)
        assertEquals("Lovelace", output.family)
        assertEquals(36, output.age)
        assertEquals(1, executor.selectExecutions)
    }

    @Test
    fun `metadata mapper uses published aliases that differ from logical output names`() {
        val executor = RecordingExecutor(
            resultSet(
                labels = listOf("user_id", "user_name", "user_family", "user_age", "user_phone"),
                rows = listOf(listOf(12, "Grace", "Hopper", 85, "555-0100")),
            ),
        )

        val output = CteQueryRowMapper.map(
            UserPhoneQueryV3::class,
            materializedRows(executor, phoneOutputs()).single(),
        )

        assertEquals(12, output.id)
        assertEquals("Grace", output.name)
        assertEquals("Hopper", output.family)
        assertEquals(85, output.age)
        assertEquals("555-0100", output.phone)
        assertEquals(1, executor.selectExecutions)
    }

    @Test
    fun `metadata mapper maps page published aliases`() {
        val executor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "name", "family", "age"),
                rows = listOf(listOf(13, "Katherine", "Johnson", 101)),
            ),
        )

        val output = CteQueryRowMapper.map(
            UserPageQueryV3::class,
            materializedRows(executor, statsOutputs()).single(),
        )

        assertEquals(13, output.id)
        assertEquals("Katherine", output.name)
        assertEquals("Johnson", output.family)
        assertEquals(101, output.age)
        assertEquals(1, executor.selectExecutions)
    }

    @Test
    fun `metadata mapper supports all v31 output declarations`() {
        val statsExecutor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "name", "family", "age"),
                rows = listOf(listOf(21, "Ada", "Lovelace", 36)),
            ),
        )
        val phoneExecutor = RecordingExecutor(
            resultSet(
                labels = listOf("user_id", "user_name", "user_family", "user_age", "user_phone"),
                rows = listOf(listOf(22, "Hedy", "Lamarr", 85, "555-0130")),
            ),
        )
        val pageExecutor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "name", "family", "age"),
                rows = listOf(listOf(23, "Katherine", "Johnson", 101)),
            ),
        )
        val displayExecutor = RecordingExecutor(
            resultSet(
                labels = listOf("id", "fullName", "age"),
                rows = listOf(listOf(24, "Grace Hopper", null)),
            ),
        )

        val stats = CteQueryRowMapper.map(
            UserStatsQueryV31::class,
            materializedRows(statsExecutor, statsOutputs()).single(),
        )
        val phone = CteQueryRowMapper.map(
            UserPhoneQueryV31::class,
            materializedRows(phoneExecutor, phoneOutputs()).single(),
        )
        val page = CteQueryRowMapper.map(
            UserPageQueryV31::class,
            materializedRows(pageExecutor, statsOutputs()).single(),
        )
        val display = CteQueryRowMapper.map(
            UserDisplayQueryV31::class,
            materializedRows(displayExecutor).single(),
        )

        assertEquals(
            listOf(21, "Ada", "Lovelace", 36),
            listOf(stats.id, stats.name, stats.family, stats.age),
        )
        assertEquals(
            listOf(22, "Hedy", "Lamarr", 85, "555-0130"),
            listOf(phone.id, phone.name, phone.family, phone.age, phone.phone),
        )
        assertEquals(
            listOf(23, "Katherine", "Johnson", 101),
            listOf(page.id, page.name, page.family, page.age),
        )
        assertEquals(listOf(24, "Grace Hopper", null), listOf(display.id, display.fullName, display.age))
        assertEquals(1, statsExecutor.selectExecutions)
        assertEquals(1, phoneExecutor.selectExecutions)
        assertEquals(1, pageExecutor.selectExecutions)
        assertEquals(1, displayExecutor.selectExecutions)
    }

    @Test
    fun `mapper does not call cte creation while mapping a row`() {
        val executor = RecordingExecutor(
            resultSet(labels = listOf("value"), rows = listOf(listOf(27))),
        )

        val row = materializedRows(executor, listOf("value" to DataType.INT)).single()
        val output = CteQueryRowMapper.map(NoExecutionQuery::class, row)

        assertEquals(27, output.value)
        assertEquals(1, executor.selectExecutions)
    }

    @Test
    fun `metadata mapper reports an annotated property whose published alias is absent`() {
        val executor = RecordingExecutor(
            resultSet(labels = listOf("id", "age"), rows = listOf(listOf(1, 36))),
        )
        val row = materializedRows(
            executor,
            listOf("id" to DataType.INT, "age" to DataType.INT),
        ).single()

        val failure = assertFailsWith<IllegalArgumentException> {
            CteQueryRowMapper.map(UserDisplayQueryV3::class, row)
        }

        assertTrue(failure.message.orEmpty().contains("fullName"))
        assertTrue(failure.message.orEmpty().contains("Missing SELECT output alias"))
    }

    @Test
    fun `metadata mapper reports a property setter failure with its alias`() {
        val executor = RecordingExecutor(
            resultSet(labels = listOf("value"), rows = listOf(listOf(5))),
        )
        val row = materializedRows(executor, listOf("value" to DataType.INT)).single()

        val failure = assertFailsWith<IllegalArgumentException> {
            CteQueryRowMapper.map(SetterFailureQuery::class, row)
        }

        assertTrue(failure.message.orEmpty().contains("value"))
        assertTrue(failure.message.orEmpty().contains("assign"))
        assertTrue(generateSequence(failure.cause) { it.cause }.any { it.message.orEmpty().contains("setter rejected") })
    }

    @Test
    fun `metadata mapper rejects output models without a no argument constructor`() {
        val executor = RecordingExecutor(
            resultSet(labels = listOf("value"), rows = listOf(listOf(5))),
        )
        val row = materializedRows(executor, listOf("value" to DataType.INT)).single()

        val failure = assertFailsWith<IllegalArgumentException> {
            CteQueryRowMapper.map(ConstructorRequiredQuery::class, row)
        }

        assertTrue(failure.message.orEmpty().contains("no-argument constructor"))
    }

    @Test
    fun `metadata mapper accepts the declared LONG materialization type without coercion`() {
        val executor = RecordingExecutor(
            resultSet(labels = listOf("value"), rows = listOf(listOf(9_000_000_000L))),
        )
        val row = materializedRows(executor, listOf("value" to DataType.LONG)).single()

        val output = CteQueryRowMapper.map(LongOutputQuery::class, row)

        assertEquals(9_000_000_000L, output.value)
    }

    private fun materializedRows(
        executor: RecordingExecutor,
        outputs: List<Pair<String, DataType>> = displayOutputs(),
    ): List<QueryRow> {
        val result = executor.queryBuilder(outputQuery(outputs)).execute()
        return assertNotNull(assertIs<ExecuteResult.Success<List<QueryRow>>>(result).result)
    }

    private fun outputQuery(outputs: List<Pair<String, DataType>>): IQueryRenderSelectApi =
        QueryRenderSelectBuilder().select {
            outputs.forEach { (outputAlias, dataType) ->
                addColumn {
                    column { tableColumn("scripted", outputAlias) }
                    alias(outputAlias)
                    execute(dataType)
                }
            }
        }

    private fun displayOutputs(): List<Pair<String, DataType>> = listOf(
        "id" to DataType.INT,
        "fullName" to DataType.STRING,
        "age" to DataType.INT,
    )

    private fun statsOutputs(): List<Pair<String, DataType>> = listOf(
        "id" to DataType.INT,
        "name" to DataType.STRING,
        "family" to DataType.STRING,
        "age" to DataType.INT,
    )

    private fun phoneOutputs(): List<Pair<String, DataType>> = listOf(
        "user_id" to DataType.INT,
        "user_name" to DataType.STRING,
        "user_family" to DataType.STRING,
        "user_age" to DataType.INT,
        "user_phone" to DataType.STRING,
    )

    class NoExecutionQuery : CteDeclaration<Unit, NoExecutionQuery>() {
        @QBQuerySelect(name = "value", alias = "value")
        var value: Int = 0

        override fun createCte(params: Unit): Cte = error("Row mapping must not call createCte")
    }

    class SetterFailureQuery : CteDeclaration<Unit, SetterFailureQuery>() {
        @QBQuerySelect(name = "value", alias = "value")
        var value: Int = 0
            set(value) {
                throw IllegalStateException("setter rejected for $value")
            }

        override fun createCte(params: Unit): Cte = error("Row mapping must not call createCte")
    }

    private class ConstructorRequiredQuery(
        initialValue: Int,
    ) : CteDeclaration<Unit, ConstructorRequiredQuery>() {
        @QBQuerySelect(name = "value", alias = "value")
        var value: Int = initialValue

        override fun createCte(params: Unit): Cte = error("Row mapping must not call createCte")
    }

    private class LongOutputQuery : CteDeclaration<Unit, LongOutputQuery>() {
        @QBQuerySelect(name = "value", alias = "value")
        var value: Long = 0L

        override fun createCte(params: Unit): Cte = error("Row mapping must not call createCte")
    }

    private class RecordingExecutor(
        private val scriptedResultSet: ResultSet,
    ) : IQueryBuilderExecutor {
        var selectExecutions: Int = 0
            private set

        override fun execute(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<ResultSet>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            selectExecutions++
            blockExecute(ExecuteResult.Success(scriptedResultSet))
        }

        override fun execute(
            queryBuilder: IQueryRenderInsertApi,
            blockExecute: (ExecuteResult<Long>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("INSERT is not used by this test")

        override fun execute(
            queryBuilder: IQueryRenderUpdateApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("UPDATE is not used by this test")

        override fun execute(
            queryBuilder: IQueryRenderDeleteApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("DELETE is not used by this test")
    }

    private fun resultSet(labels: List<String>, rows: List<List<Any?>>): ResultSet {
        var rowIndex = -1
        var lastWasNull = false
        var closed = false
        val metadata = proxy(ResultSetMetaData::class.java) { _, method, arguments ->
            when (method.name) {
                "getColumnCount" -> labels.size
                "getColumnLabel", "getColumnName" -> labels[(arguments!![0] as Int) - 1]
                "isNullable" -> ResultSetMetaData.columnNullableUnknown
                "toString" -> "TypedOutputResultSetMetaData"
                else -> error("Unexpected metadata call: ${method.name}")
            }
        }
        return proxy(ResultSet::class.java) { _, method, arguments ->
            when (method.name) {
                "next" -> ++rowIndex < rows.size
                "getMetaData" -> metadata
                "getInt" -> {
                    val value = rows[rowIndex][(arguments!![0] as Int) - 1]
                    lastWasNull = value == null
                    (value as? Number)?.toInt() ?: 0
                }
                "getLong" -> {
                    val value = rows[rowIndex][(arguments!![0] as Int) - 1]
                    lastWasNull = value == null
                    (value as? Number)?.toLong() ?: 0L
                }
                "getString" -> {
                    val value = rows[rowIndex][(arguments!![0] as Int) - 1]
                    lastWasNull = value == null
                    value as String?
                }
                "wasNull" -> lastWasNull
                "close" -> { closed = true; null }
                "isClosed" -> closed
                "toString" -> "TypedOutputResultSet"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === arguments?.get(0)
                else -> error("Unexpected ResultSet call: ${method.name}")
            }
        }
    }

    private fun <T> proxy(type: Class<T>, handler: InvocationHandler): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
}
