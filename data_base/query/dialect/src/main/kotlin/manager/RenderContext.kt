package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

class RenderContext(
    override val dialect: ISqlDialect,
    override val registry: IRendererRegistry
) : IParameterRenderContext
{
    private var renderSession: ParameterRenderSession? = null

    override val parameterOccurrences: MutableList<IQueryParameterBinding>?
        get() = renderSession?.occurrences

    internal constructor(
        dialect: ISqlDialect,
        registry: IRendererRegistry,
        renderSession: ParameterRenderSession,
    ) : this(dialect, registry) {
        this.renderSession = renderSession
    }

    override fun renderParameter(parameter: SqlParameter<*>): String {
        val session = renderSession
        session?.recordLegacyBinding(parameter)
        return if (session?.placeholderMode == ParameterPlaceholderMode.Positional) {
            "?"
        } else {
            ":${parameter.name}"
        }
    }

    override fun renderParameter(parameter: IQueryParameterAst<*>): String {
        val session = renderSession
            ?: return ":${requireNotNull(parameter.metadata.name) { "Parameter AST metadata name is missing" }}"
        val binding = session.parameterFor(parameter)
        session.recordAst(binding)
        return if (session.placeholderMode == ParameterPlaceholderMode.Positional) "?" else ":${binding.name}"
    }

    override fun renderLegacyPlaceholder(name: String): String {
        val session = renderSession
        session?.recordLegacyPlaceholder()
        return if (session?.placeholderMode == ParameterPlaceholderMode.Positional) "?" else ":$name"
    }

    override fun recordLegacyParameterSource() {
        renderSession?.recordLegacyPlaceholder()
    }

    override fun rollbackParameterOccurrences(startIndex: Int) {
        val session = renderSession
        if (session != null) {
            session.rollbackToOccurrenceCount(startIndex)
            return
        }

        parameterOccurrences?.let { occurrences ->
            while (occurrences.size > startIndex) occurrences.removeAt(occurrences.lastIndex)
        }
    }

}
