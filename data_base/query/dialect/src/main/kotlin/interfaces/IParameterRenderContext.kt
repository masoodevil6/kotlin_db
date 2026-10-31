package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

/** Parameter emission helper available to occurrence-aware renderer implementations. */
interface IParameterRenderContext : IRenderContext {
    /** Emits the logical placeholder or positional placeholder and records its binding occurrence. */
    override fun renderParameter(parameter: SqlParameter<*>): String

    /** Emits a parameter AST node using its stable per-query execution adapter. */
    override fun renderParameter(parameter: IQueryParameterAst<*>): String

    /** Records a legacy placeholder source whose value is still supplied by the legacy params list. */
    override fun renderLegacyPlaceholder(name: String): String

    /** Records a raw SQL expression that may contain legacy named placeholders. */
    override fun recordLegacyParameterSource()

    /** Removes emissions made after [startIndex], including their emission-count bookkeeping. */
    fun rollbackParameterOccurrences(startIndex: Int)
}
