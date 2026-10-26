package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.query.BoundQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.data_base.query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import gog.my_project.tools.scripts.StringTools
import java.sql.ResultSet

class QueryBuilderExecutor : IQueryBuilderExecutor {

    override fun execute(
        queryBuilder:   IQueryRenderSelectApi,
        blockExecute:   (ExecuteResult<ResultSet>) -> Unit,
        blockQueryInfo: ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)?
    ){
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.render(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                QueryExecute().executeSelect(builtQuery, blockExecute)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                QueryExecute().executeSelect(
                    BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences),
                    blockExecute,
                )
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderInsertApi,
        blockExecute: (ExecuteResult<Long>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> QueryExecute().executeInsert(
                legacyBuiltQuery(rendered, blockQueryInfo),
                blockExecute,
            )
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                QueryExecute().executeInsert(
                    BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences),
                    blockExecute,
                )
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderUpdateApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> QueryExecute().executeUpdate(
                legacyBuiltQuery(rendered, blockQueryInfo),
                blockExecute,
            )
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                QueryExecute().executeUpdate(
                    BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences),
                    blockExecute,
                )
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderDeleteApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> QueryExecute().executeDelete(
                legacyBuiltQuery(rendered, blockQueryInfo),
                blockExecute,
            )
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                QueryExecute().executeDelete(
                    BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences),
                    blockExecute,
                )
            }
        }
    }

    private fun legacyBuiltQuery(
        rendered: LegacyQueryRenderResult,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ): BuiltQuery {
        val sql = requireNotNull(rendered.sql) { "rendered sql is null" }
        if (blockQueryInfo != null) {
            blockQueryInfo(StringTools.formatSql(sql), mutableMapOf())
        }
        return BuiltQuery(sql, mutableListOf())
    }

    private fun reportIdentityAwareQuery(
        rendered: IdentityAwareQueryRenderResult,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        if (blockQueryInfo != null) {
            val paramsMap = parameterMap(rendered.parameterOccurrences)
            blockQueryInfo(StringTools.formatSql(rendered.displaySql), paramsMap)
        }
    }

    /** The callback exposes the first emitted value for each name; binding order stays in the render result. */
    private fun parameterMap(
        occurrences: List<IQueryParameterBinding>,
    ): MutableMap<String, Any?> = linkedMapOf<String, Any?>().apply {
        occurrences.forEach { parameter ->
            putIfAbsent(parameter.name, parameter.value)
        }
    }

}
