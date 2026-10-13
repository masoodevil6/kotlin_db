package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import kotlin.reflect.KClass

/** Keeps one occurrence collector available across recursive renderer dispatch. */
internal class RenderSessionRegistry(
    private val delegate: IRendererRegistry,
    private val session: ParameterRenderSession,
) : IRendererRegistry {

    override val renderers: MutableMap<KClass<*>, IAstRenderer<*, *>>
        get() = delegate.renderers

    override fun <A : Any, D : QueryDataClass?> register(
        renderClass: KClass<A>,
        renderer: IAstRenderer<A, D>,
    ) {
        check(!session.requireOccurrenceAwareRenderers) {
            "Renderer registration is disabled during identity-aware rendering"
        }
        delegate.register(renderClass, renderer)
    }

    override fun <A : Any, D : QueryDataClass?> get(type: KClass<A>): IAstRenderer<A, D>? =
        delegate.get(type)

    @Suppress("UNCHECKED_CAST")
    override fun render(
        ast: Any?,
        dialect: ISqlDialect,
        dataClass: QueryDataClass?,
    ): String? {
        if (ast == null) return ""

        val rendererEntry = renderers.entries.firstOrNull {
            it.key.java.isAssignableFrom(ast::class.java)
        } ?: error("No renderer registered for ${ast::class}")

        val renderer = rendererEntry.value as IAstRenderer<Any, QueryDataClass?>
        if (session.requireOccurrenceAwareRenderers && renderer !is IParameterOccurrenceAwareRenderer) {
            throw UnsupportedOperationException(
                "Identity-aware rendering does not support unverified renderer ${renderer::class.qualifiedName} " +
                    "for ${rendererEntry.key.qualifiedName}"
            )
        }
        val occurrenceStart = session.occurrences.size
        val rendered = renderer.render(
            ast,
            RenderContext(dialect, this, session),
            dataClass,
        )
        if (rendered == null) session.rollbackTo(occurrenceStart)
        return rendered
    }
}
