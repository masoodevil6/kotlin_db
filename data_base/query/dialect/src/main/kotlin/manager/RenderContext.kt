package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import gog.my_project.data_base.core.query.reader.SqlParameter

class RenderContext(
    override val dialect: ISqlDialect,
    override val registry: IRendererRegistry
) : IParameterRenderContext
{
    private var renderSession: ParameterRenderSession? = null

    override val parameterOccurrences: MutableList<Any>?
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
        session?.record(parameter)
        return if (session?.placeholderMode == ParameterPlaceholderMode.Positional) {
            "?"
        } else {
            ":${parameter.name}"
        }
    }

    override fun rollbackParameterOccurrences(startIndex: Int) {
        val session = renderSession
        if (session != null) {
            session.rollbackTo(startIndex)
            return
        }

        parameterOccurrences?.let { occurrences ->
            while (occurrences.size > startIndex) occurrences.removeAt(occurrences.lastIndex)
        }
    }

}
