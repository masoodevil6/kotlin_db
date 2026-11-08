package gog.my_project.data_base_migration.core.system_migration.cte

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base_query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base_query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base_query.cte.declaration.CteDeclaration
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.renderer.dialects.MySqlDialect
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemMigrationStatusQueryTest {
    @Test
    fun `status CTE reuses all source CTEs and left joins related rows`() {
        val status = SystemMigrationStatusQuery().createCte(SystemMigrationStatusQuery.RelationFilters())
        val ast = status.definition.ast as IQueryRenderSelectAst

        assertEquals(
            listOf(
                "system_migration_tag_query",
                "system_migration_version_query",
                "system_migration_query",
            ),
            status.dependencies.map { it.name },
        )
        assertEquals(
            listOf(
                "tagId",
                "tagName",
                "tagType",
                "tagVersionId",
                "migrationHistoryId",
                "appliedVersionReferenceId",
                "appliedVersionId",
                "appliedVersionTagId",
                "appliedVersionTagName",
                "appliedVersionTagType",
            ),
            ast.select?.columns?.map { it.metadata.ColumnAlias },
        )
        assertEquals("system_migration_tag_query", ast.table?.metadata?.resolveName())
        assertEquals("status_tag", ast.table?.metadata?.resolveAlias())
        assertEquals(
            listOf(
                "system_migration_tag_query",
                "system_migration_version_query",
                "system_migration_query",
            ),
            ast.withs?.withs?.map { it.metadata.withName },
        )
        val joins = requireNotNull(ast.joins).joins
        assertEquals(4, joins.size)
        assertTrue(joins.all { it.metadata.joinType == "left join" })
        assertEquals(
            listOf(
                "system_migration_version_query",
                "system_migration_query",
                "system_migration_version_query",
                "system_migration_tag_query",
            ),
            joins.map { it.joinTable.metadata.resolveName() },
        )
        assertEquals(listOf("tag_version", "applied_migration", "applied_version", "applied_version_tag"),
            joins.map { it.joinTable.metadata.resolveAlias() })
        assertNull(ast.where)
    }

    @Test
    fun `rendered status query retains orphan and unapplied tag rows through left joins`() {
        val status = SystemMigrationStatusQuery().createCte(SystemMigrationStatusQuery.RelationFilters())
        val rendered = render(status.definition.ast)
        val sql = rendered.sql.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(sql.contains("with system_migration_tag_query as"), sql)
        assertTrue(sql.contains("system_migration_version_query as"), sql)
        assertTrue(sql.contains("system_migration_query as"), sql)
        assertTrue(sql.contains("from system_migration_tag_query as status_tag"), sql)
        assertEquals(4, Regex("left join").findAll(sql).count())
        assertTrue(!sql.contains("inner join"), sql)
        assertTrue(sql.contains("status_tag.id = tag_version.tagid"), sql)
        assertTrue(sql.contains("status_tag.id = applied_migration.tagid"), sql)
        assertTrue(sql.contains("applied_migration.versionid = applied_version.id"), sql)
        assertTrue(sql.contains("applied_version.tagid = applied_version_tag.id"), sql)
        assertTrue(sql.contains("as migrationhistoryid"), sql)
        assertTrue(sql.contains("as appliedversionid"), sql)
        assertTrue(sql.contains("as appliedversionreferenceid"), sql)
        assertTrue(!sql.contains("select distinct"), sql)
        assertTrue(!sql.contains("group by"), sql)
        assertEquals(emptyList(), rendered.parameterValues)
    }

    @Test
    fun `tag filters bind at the outer query without filtering joined source CTEs`() {
        val status = SystemMigrationStatusQuery().createCte(
            SystemMigrationStatusQuery.RelationFilters(tagId = 17, tagType = 0),
        )
        val rendered = render(status.definition.ast)
        val sql = rendered.sql.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(sql.contains("where"), sql)
        assertTrue(sql.contains("status_tag.id = :system_migration_status_tag_id"), sql)
        assertTrue(sql.contains("status_tag.tagtype = :system_migration_status_tag_type"), sql)
        assertEquals(listOf(17, 0), rendered.parameterValues)
        assertEquals(4, Regex("left join").findAll(sql).count())
    }

    @Test
    fun `optional relationship outputs are nullable`() {
        val status = SystemMigrationStatusQuery()

        assertNull(status.tagVersionId)
        assertNull(status.migrationHistoryId)
        assertNull(status.appliedVersionReferenceId)
        assertNull(status.appliedVersionId)
        assertNull(status.appliedVersionTagId)
        assertNull(status.appliedVersionTagName)
        assertNull(status.appliedVersionTagType)
    }

    @Test
    fun `status declaration is reusable and maps nullable outputs through the shared CTE mapper`() {
        val status = SystemMigrationStatusQuery()
        assertIs<CteDeclaration<*, *>>(status)

        val labels = listOf(
            "tagId",
            "tagName",
            "tagType",
            "tagVersionId",
            "migrationHistoryId",
            "appliedVersionReferenceId",
            "appliedVersionId",
            "appliedVersionTagId",
            "appliedVersionTagName",
            "appliedVersionTagType",
        )
        val values = listOf<Any?>(31, "release", 0, null, null, null, null, null, null, null)
        val executor = SingleRowExecutor(resultSet(labels, values))

        val result = status.execute(executor, SystemMigrationStatusQuery.RelationFilters())
        val success = assertIs<ExecuteResult.Success<gog.my_project.collection.ICollection<SystemMigrationStatusQuery>>>(result)
        val mapped = requireNotNull(success.result).get(0)

        assertEquals(31, mapped.tagId)
        assertEquals("release", mapped.tagName)
        assertEquals(0, mapped.tagType)
        assertNull(mapped.tagVersionId)
        assertNull(mapped.migrationHistoryId)
        assertNull(mapped.appliedVersionReferenceId)
        assertNull(mapped.appliedVersionId)
        assertNull(mapped.appliedVersionTagId)
        assertNull(mapped.appliedVersionTagName)
        assertNull(mapped.appliedVersionTagType)
        assertEquals(1, executor.selectCalls)
    }

    private fun render(ast: IQueryRenderSelectAst): RenderedQuery =
        when (val result = QueryParameterRendering.render(ast, MySqlDialect())) {
            is IdentityAwareQueryRenderResult -> RenderedQuery(
                result.displaySql,
                result.parameterOccurrences.map { it.value },
            )
            is LegacyQueryRenderResult -> RenderedQuery(requireNotNull(result.sql), emptyList())
        }

    private data class RenderedQuery(
        val sql: String,
        val parameterValues: List<Any?>,
    )

    private class SingleRowExecutor(
        private val resultSet: ResultSet,
    ) : IQueryBuilderExecutor {
        var selectCalls: Int = 0
            private set

        override fun execute(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<ResultSet>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            selectCalls++
            blockExecute(ExecuteResult.Success(resultSet))
        }

        override fun execute(
            queryBuilder: IQueryRenderInsertApi,
            blockExecute: (ExecuteResult<Long>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used")

        override fun execute(
            queryBuilder: IQueryRenderUpdateApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used")

        override fun execute(
            queryBuilder: IQueryRenderDeleteApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used")
    }

    private fun resultSet(labels: List<String>, values: List<Any?>): ResultSet {
        var cursor = -1
        var wasNull = false
        val metadata = Proxy.newProxyInstance(
            ResultSetMetaData::class.java.classLoader,
            arrayOf(ResultSetMetaData::class.java),
        ) { _, method, args ->
            val index = (args?.firstOrNull() as? Int)?.minus(1)
            when (method.name) {
                "getColumnCount" -> labels.size
                "getColumnLabel", "getColumnName" -> labels[requireNotNull(index)]
                else -> defaultValue(method.returnType)
            }
        } as ResultSetMetaData

        return Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getMetaData" -> metadata
                "next" -> (++cursor == 0)
                "getInt", "getString", "getLong", "getObject" -> {
                    val index = (args?.firstOrNull() as Int) - 1
                    val value = values[index]
                    wasNull = value == null
                    when (method.name) {
                        "getInt" -> value as? Int ?: 0
                        "getLong" -> (value as? Number)?.toLong() ?: 0L
                        else -> value
                    }
                }
                "wasNull" -> wasNull
                "close" -> null
                else -> defaultValue(method.returnType)
            }
        } as ResultSet
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
