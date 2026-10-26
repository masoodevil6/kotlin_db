package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

/** Parameter emission helper available to occurrence-aware renderer implementations. */
interface IParameterRenderContext : IRenderContext {
    /** Emits the logical placeholder or positional placeholder and records its binding occurrence. */
    fun renderParameter(parameter: SqlParameter<*>): String

    /** Emits a parameter AST node using its stable per-query execution adapter. */
    fun renderParameter(parameter: IQueryParameterAst<*>): String

    /** Records a legacy placeholder source whose value is still supplied by the legacy params list. */
    fun renderLegacyPlaceholder(name: String): String

    /** Records a raw SQL expression that may contain legacy named placeholders. */
    fun recordLegacyParameterSource()

    /** Removes emissions made after [startIndex], including their emission-count bookkeeping. */
    fun rollbackParameterOccurrences(startIndex: Int)
}

/** Preserves named rendering for contexts that do not opt into the parameter render contract. */
fun IRenderContext.renderParameter(parameter: SqlParameter<*>): String =
    (this as? IParameterRenderContext)?.renderParameter(parameter) ?: ":${parameter.name}"

/** Preserves named rendering for contexts that do not opt into the parameter render contract. */
fun IRenderContext.renderParameter(parameter: IQueryParameterAst<*>): String =
    (this as? IParameterRenderContext)?.renderParameter(parameter)
        ?: ":${requireNotNull(parameter.metadata.name) { "Parameter AST metadata name is missing" }}"

/** Emits a named placeholder and records it as a legacy source when a render session is active. */
fun IRenderContext.renderLegacyPlaceholder(name: String): String =
    (this as? IParameterRenderContext)?.renderLegacyPlaceholder(name) ?: ":$name"

/** Records a raw SQL expression as a legacy parameter source when a render session is active. */
fun IRenderContext.recordLegacyParameterSource() {
    (this as? IParameterRenderContext)?.recordLegacyParameterSource()
}
