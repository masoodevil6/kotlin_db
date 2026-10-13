package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.query.BoundQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.meta_data.parameters.QueryParameterMetadataExtractor
import gog.my_project.data_base.query.meta_data.parameters.QueryParameterRepresentation
import gog.my_project.data_base.query.renderer.manager.DialectSelector
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
        val representation = QueryParameterMetadataExtractor.inspect(queryBuilder.ast).representation
        when (representation) {
            QueryParameterRepresentation.LegacyOnly -> executeOut(
                queryBuilder = queryBuilder,
                blockQueryInfo = blockQueryInfo,
                blockResult = { db: IQueryExecute, builtQuery: BuiltQuery ->
                    db.executeSelect(
                        builtQuery = builtQuery,
                        blockExecute = blockExecute,
                    )
                },
            )

            QueryParameterRepresentation.MixedOrUnsupported -> throw UnsupportedOperationException(
                "Identity-aware execution is not supported for mixed or unsupported parameter ASTs",
            )

            QueryParameterRepresentation.IdentityAware -> {
                val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
                val rendered = QueryParameterRendering.render(queryBuilder.ast, dialect)
                    as? IdentityAwareQueryRenderResult
                    ?: error("Identity-aware parameter metadata produced a legacy render result")

                if (blockQueryInfo != null) {
                    val paramsMap = queryBuilder.params
                        .associate { parameter -> parameter.name to parameter.value }
                        .toMutableMap()
                    blockQueryInfo(StringTools.formatSql(rendered.displaySql), paramsMap)
                }

                val db: IQueryExecute = QueryExecute()
                db.executeSelect(
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
        when (QueryParameterMetadataExtractor.inspect(queryBuilder.ast).representation) {
            QueryParameterRepresentation.LegacyOnly -> executeOut(
                queryBuilder = queryBuilder,
                blockQueryInfo = blockQueryInfo,
                blockResult = { db: IQueryExecute, builtQuery: BuiltQuery ->
                    db.executeInsert(builtQuery = builtQuery, blockExecute = blockExecute)
                },
            )
            QueryParameterRepresentation.MixedOrUnsupported -> throw UnsupportedOperationException(
                "Identity-aware execution is not supported for mixed or unsupported parameter ASTs",
            )
            QueryParameterRepresentation.IdentityAware -> QueryExecute().executeInsert(
                renderBoundDml(queryBuilder.ast, queryBuilder.params, blockQueryInfo),
                blockExecute,
            )
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderUpdateApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        when (QueryParameterMetadataExtractor.inspect(queryBuilder.ast).representation) {
            QueryParameterRepresentation.LegacyOnly -> executeOut(
                queryBuilder = queryBuilder,
                blockQueryInfo = blockQueryInfo,
                blockResult = { db: IQueryExecute, builtQuery: BuiltQuery ->
                    db.executeUpdate(builtQuery = builtQuery, blockExecute = blockExecute)
                },
            )
            QueryParameterRepresentation.MixedOrUnsupported -> throw UnsupportedOperationException(
                "Identity-aware execution is not supported for mixed or unsupported parameter ASTs",
            )
            QueryParameterRepresentation.IdentityAware -> QueryExecute().executeUpdate(
                renderBoundDml(queryBuilder.ast, queryBuilder.params, blockQueryInfo),
                blockExecute,
            )
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderDeleteApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        when (QueryParameterMetadataExtractor.inspect(queryBuilder.ast).representation) {
            QueryParameterRepresentation.LegacyOnly -> executeOut(
                queryBuilder = queryBuilder,
                blockQueryInfo = blockQueryInfo,
                blockResult = { db: IQueryExecute, builtQuery: BuiltQuery ->
                    db.executeDelete(builtQuery = builtQuery, blockExecute = blockExecute)
                },
            )
            QueryParameterRepresentation.MixedOrUnsupported -> throw UnsupportedOperationException(
                "Identity-aware execution is not supported for mixed or unsupported parameter ASTs",
            )
            QueryParameterRepresentation.IdentityAware -> QueryExecute().executeDelete(
                renderBoundDml(queryBuilder.ast, queryBuilder.params, blockQueryInfo),
                blockExecute,
            )
        }
    }

    private fun renderBoundDml(
        ast: IQueryAst,
        params: List<SqlParameter<*>>,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ): BoundQuery {
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        val rendered = QueryParameterRendering.renderDml(ast, dialect) as? IdentityAwareQueryRenderResult
            ?: error("Identity-aware DML metadata produced a legacy render result")

        if (blockQueryInfo != null) {
            val paramsMap = params.associate { parameter -> parameter.name to parameter.value }.toMutableMap()
            blockQueryInfo(StringTools.formatSql(rendered.displaySql), paramsMap)
        }

        return BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
    }

}
