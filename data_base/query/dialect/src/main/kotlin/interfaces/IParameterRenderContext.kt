package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter

/** Parameter emission helper available to occurrence-aware renderer implementations. */
interface IParameterRenderContext : IRenderContext {
    /** Emits the logical placeholder or positional placeholder and records its binding occurrence. */
    fun renderParameter(parameter: SqlParameter<*>): String

    /** Removes emissions made after [startIndex], including their emission-count bookkeeping. */
    fun rollbackParameterOccurrences(startIndex: Int)
}

/** Preserves named rendering for contexts that do not opt into the parameter render contract. */
fun IRenderContext.renderParameter(parameter: SqlParameter<*>): String =
    (this as? IParameterRenderContext)?.renderParameter(parameter) ?: ":${parameter.name}"
