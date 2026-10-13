package gog.my_project.data_base.query.renderer.parameters

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.meta_data.parameters.QueryParameterMetadataExtractor
import gog.my_project.data_base.query.meta_data.parameters.QueryParameterRepresentation
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect

sealed interface QueryParameterRenderResult

/** The unchanged name-based SQL path for ASTs without typed parameter references. */
data class LegacyQueryRenderResult(val sql: String?) : QueryParameterRenderResult

/** Two SQL forms and the occurrence-ordered bindings for an identity-aware query. */
data class IdentityAwareQueryRenderResult(
    val displaySql: String,
    val jdbcSql: String,
    val parameterOccurrences: List<SqlParameter<*>>,
) : QueryParameterRenderResult

/** Classifies query ASTs before choosing the legacy or identity-aware renderer path. */
object QueryParameterRendering {
    fun render(
        ast: IQueryRenderSelectAst,
        dialect: ISqlDialect,
    ): QueryParameterRenderResult = renderClassified(ast, dialect)

    /** DML entry point used by query:executor for INSERT, UPDATE, and DELETE ASTs. */
    fun renderDml(
        ast: IQueryAst,
        dialect: ISqlDialect,
    ): QueryParameterRenderResult {
        require(ast is IQueryRenderInsertAst || ast is IQueryRenderUpdateAst || ast is IQueryRenderDeleteAst) {
            "Identity-aware DML rendering requires an INSERT, UPDATE, or DELETE AST"
        }
        return renderClassified(ast, dialect)
    }

    private fun renderClassified(
        ast: IQueryAst,
        dialect: ISqlDialect,
    ): QueryParameterRenderResult {
        val metadata = QueryParameterMetadataExtractor.inspect(ast)
        return when (metadata.representation) {
            QueryParameterRepresentation.LegacyOnly ->
                LegacyQueryRenderResult(dialect.render(ast))

            QueryParameterRepresentation.MixedOrUnsupported ->
                throw UnsupportedOperationException(
                    "Identity-aware rendering is not supported for mixed or unsupported parameter ASTs"
                )

            QueryParameterRepresentation.IdentityAware -> {
                val mysqlDialect = dialect as? MySqlDialect
                    ?: throw UnsupportedOperationException(
                        "Identity-aware rendering requires the verified MySQL/MariaDB dual-render contract; " +
                            "${dialect::class.qualifiedName} does not provide it"
                    )
                val rendered = mysqlDialect.renderIdentityAware(ast)
                val validatedOccurrences = metadata.validateOccurrences(rendered.parameterOccurrences)
                IdentityAwareQueryRenderResult(
                    displaySql = rendered.displaySql,
                    jdbcSql = rendered.jdbcSql,
                    parameterOccurrences = validatedOccurrences,
                )
            }
        }
    }
}
