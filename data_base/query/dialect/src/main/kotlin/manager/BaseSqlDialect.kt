package gog.my_project.data_base.query.dialect.manager

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.query.dialect.data_class.parameter_data.ParameterRenderAttempt
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import gog.my_project.data_base.query.renderer.manager.RenderSessionRegistry
import gog.my_project.data_base.query.renderer.manager.RendererRegistry
import gog.my_project.data_base.query.renderer.manager.ParameterPlaceholderMode
import gog.my_project.data_base.query.renderer.manager.ParameterRenderSession
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

abstract class BaseSqlDialect(
) : ISqlDialect{

    override val _registry: IRendererRegistry = RendererRegistry()

    init {
        registerRenders()
    }


    override fun <Ast : IQueryAst<*>> render(
        ast: Ast
    ): String? {
        return _registry.render(ast , this , null);
    }

    /** Renders once while collecting parameter bindings in placeholder-emission order. */
    fun renderWithBindings(ast: IQueryAst<*>): Pair<String?, List<IQueryParameterBinding>> {
        val session = ParameterRenderSession(ParameterPlaceholderMode.Named, false)
        val sessionRegistry = RenderSessionRegistry(_registry, session)
        val sql = sessionRegistry.render(ast, this, null)
        return sql to session.occurrences.toList()
    }

    /**
     * Renders once with named placeholders, classifies only parameter sources actually emitted,
     * and renders a positional form only when a binding-backed parameter was emitted.
     */
    protected fun renderParameterAwarePasses(ast: IQueryAst<*>): ParameterRenderAttempt {
        val rendererSnapshot = SnapshotRendererRegistry(_registry.renderers)
        val bindingSnapshots = IdentityHashMap<IQueryParameterAst<*>, IQueryParameterBinding>()
        val namedSession = ParameterRenderSession(
            placeholderMode = ParameterPlaceholderMode.Named,
            requireOccurrenceAwareRenderers = true,
            generatedBindings = bindingSnapshots,
        )
        val namedSql = RenderSessionRegistry(rendererSnapshot, namedSession).render(ast, this, null)
        val namedOccurrences = typedOccurrences(namedSession, "named")

        if (namedSession.astParameterOccurrenceCount > 0 &&
            (namedSession.legacyBindingOccurrenceCount > 0 || namedSession.legacyPlaceholderCount > 0)
        ) {
            throw UnsupportedOperationException(
                "A query cannot mix independent parameter AST nodes with legacy parameter sources",
            )
        }
        if (namedSession.legacyBindingOccurrenceCount > 0 && namedSession.legacyPlaceholderCount > 0) {
            throw UnsupportedOperationException(
                "A query cannot mix SqlParameter bindings with legacy unbound placeholder sources",
            )
        }

        if (namedSession.astParameterOccurrenceCount == 0 && namedSession.legacyBindingOccurrenceCount == 0) {
            return ParameterRenderAttempt(
                namedSql = namedSql,
                positionalSql = null,
                parameterOccurrences = namedOccurrences,
                parameterAstOccurrenceCount = 0,
                legacyBindingOccurrenceCount = namedSession.legacyBindingOccurrenceCount,
                legacyPlaceholderCount = namedSession.legacyPlaceholderCount,
            )
        }

        val positionalSession = ParameterRenderSession(
            placeholderMode = ParameterPlaceholderMode.Positional,
            requireOccurrenceAwareRenderers = true,
            generatedBindings = bindingSnapshots,
        )
        val positionalSql = RenderSessionRegistry(rendererSnapshot, positionalSession).render(ast, this, null)
        val positionalOccurrences = typedOccurrences(positionalSession, "positional")
        check(namedSession.emittedParameterCount == positionalSession.emittedParameterCount) {
            "Named and positional rendering emitted different parameter counts"
        }
        check(namedOccurrences.size == positionalOccurrences.size) {
            "Named and positional rendering recorded different occurrence counts"
        }
        check(namedSession.astParameterOccurrenceCount == positionalSession.astParameterOccurrenceCount) {
            "Named and positional rendering emitted different AST parameter counts"
        }
        check(namedSession.legacyBindingOccurrenceCount == positionalSession.legacyBindingOccurrenceCount) {
            "Named and positional rendering emitted different legacy binding counts"
        }
        check(namedSession.legacyPlaceholderCount == positionalSession.legacyPlaceholderCount) {
            "Named and positional rendering emitted different legacy placeholder counts"
        }
        check(namedOccurrences.indices.all { namedOccurrences[it] === positionalOccurrences[it] }) {
            "Named and positional rendering emitted different parameter occurrence sequences"
        }

        return ParameterRenderAttempt(
            namedSql = namedSql,
            positionalSql = positionalSql,
            parameterOccurrences = positionalOccurrences,
            parameterAstOccurrenceCount = namedSession.astParameterOccurrenceCount,
            legacyBindingOccurrenceCount = namedSession.legacyBindingOccurrenceCount,
            legacyPlaceholderCount = namedSession.legacyPlaceholderCount,
        )
    }

    private fun typedOccurrences(session: ParameterRenderSession, passName: String): List<IQueryParameterBinding> =
        session.validatedOccurrences(passName)

    private class SnapshotRendererRegistry(
        renderers: Map<KClass<*>, IAstRenderer<*, *>>,
    ) : IRendererRegistry {
        @Suppress("UNCHECKED_CAST")
        override val renderers: MutableMap<KClass<*>, IAstRenderer<*, *>> =
            Collections.unmodifiableMap(LinkedHashMap(renderers)) as MutableMap<KClass<*>, IAstRenderer<*, *>>

        override fun <A : Any, D : QueryDataClass?> register(
            renderClass: KClass<A>,
            renderer: IAstRenderer<A, D>,
        ) {
            throw UnsupportedOperationException("Renderer registry is immutable during parameter-aware rendering")
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any, D : QueryDataClass?> get(type: KClass<A>): IAstRenderer<A, D>? =
            renderers[type] as? IAstRenderer<A, D>

        override fun render(ast: Any?, dialect: ISqlDialect, dataClass: QueryDataClass?): String? =
            error("Parameter-aware rendering must dispatch through RenderSessionRegistry")
    }


}
