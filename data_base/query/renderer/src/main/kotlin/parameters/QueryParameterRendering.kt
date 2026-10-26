package gog.my_project.data_base.query.renderer.parameters

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect

sealed interface QueryParameterRenderResult

/** The name-based SQL path when this render emitted no binding-backed parameter occurrence. */
data class LegacyQueryRenderResult(val sql: String?) : QueryParameterRenderResult

/** Two SQL forms and the occurrence-ordered bindings for an identity-aware query. */
data class IdentityAwareQueryRenderResult(
    val displaySql: String,
    val jdbcSql: String,
    val parameterOccurrences: List<IQueryParameterBinding>,
) : QueryParameterRenderResult

/** Collects parameter sources from the renderer emission stream. */
object QueryParameterRendering {
    fun render(
        ast: IQueryRenderSelectAst,
        dialect: ISqlDialect,
    ): QueryParameterRenderResult = renderClassified(ast, dialect)

    /** DML entry point used by query:executor for INSERT, UPDATE, and DELETE ASTs. */
    fun renderDml(
        ast: IQueryAst<*>,
        dialect: ISqlDialect,
    ): QueryParameterRenderResult {
        require(ast is IQueryRenderInsertAst || ast is IQueryRenderUpdateAst || ast is IQueryRenderDeleteAst) {
            "Identity-aware DML rendering requires an INSERT, UPDATE, or DELETE AST"
        }
        return renderClassified(ast, dialect)
    }

    private fun renderClassified(
        ast: IQueryAst<*>,
        dialect: ISqlDialect,
    ): QueryParameterRenderResult {
        val mysqlDialect = dialect as? MySqlDialect
            ?: throw UnsupportedOperationException(
                "Parameter-aware rendering requires the verified MySQL/MariaDB occurrence-rendering contract; " +
                    "${dialect::class.qualifiedName} does not provide it"
            )
        val rendered = mysqlDialect.renderParameterAware(ast)
        if (rendered.parameterAstOccurrenceCount == 0 && rendered.legacyBindingOccurrenceCount == 0) {
            return LegacyQueryRenderResult(rendered.namedSql)
        }

        return IdentityAwareQueryRenderResult(
            displaySql = requireNotNull(rendered.namedSql) { "Parameter-aware query rendered no display SQL" },
            jdbcSql = requireNotNull(rendered.positionalSql) { "Parameter-aware query rendered no positional SQL" },
            parameterOccurrences = rendered.parameterOccurrences,
        )
    }
}
