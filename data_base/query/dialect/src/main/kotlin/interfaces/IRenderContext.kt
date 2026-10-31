package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect

interface IRenderContext {
    val registry:  IRendererRegistry;
    val dialect:   ISqlDialect;

    /** Ordered parameter references captured by an opt-in render session. */
    val parameterOccurrences: MutableList<IQueryParameterBinding>?
        get() = null

    /** Preserves named rendering for contexts that do not opt into the parameter render contract. */
    fun renderParameter(parameter: SqlParameter<*>): String =
        (this as? IParameterRenderContext)?.renderParameter(parameter) ?: ":${parameter.name}"

    /** Preserves named rendering for contexts that do not opt into the parameter render contract. */
    fun renderParameter(parameter: IQueryParameterAst<*>): String =
        (this as? IParameterRenderContext)?.renderParameter(parameter)
            ?: ":${requireNotNull(parameter.metadata.name) { "Parameter AST metadata name is missing" }}"

    /** Emits a named placeholder and records it as a legacy source when a render session is active. */
    fun renderLegacyPlaceholder(name: String): String =
        (this as? IParameterRenderContext)?.renderLegacyPlaceholder(name) ?: ":$name"

    /** Records a raw SQL expression as a legacy parameter source when a render session is active. */
    fun recordLegacyParameterSource() {
        (this as? IParameterRenderContext)?.recordLegacyParameterSource()
    }
}
