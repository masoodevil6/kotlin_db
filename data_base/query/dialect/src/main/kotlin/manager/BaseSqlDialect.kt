package gog.my_project.data_base.query.dialect.manager

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.dialect.data_class.RenderedQuery
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import gog.my_project.data_base.query.renderer.manager.RenderSessionRegistry
import gog.my_project.data_base.query.renderer.manager.RendererRegistry
import gog.my_project.data_base.query.renderer.manager.ParameterPlaceholderMode
import gog.my_project.data_base.query.renderer.manager.ParameterRenderSession
import java.util.Collections
import kotlin.reflect.KClass
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

abstract class BaseSqlDialect(
) : ISqlDialect{

    override val _registry: IRendererRegistry = RendererRegistry()

    init {
        registerRenders()
    }


    override fun <Ast : IQueryAst> render(
        ast: Ast
    ): String? {
        return _registry.render(ast , this , null);
    }

    /** Renders once while collecting Builder parameter references in placeholder order. */
    fun renderWithBindings(ast: IQueryRenderSelectAst): Pair<String?, List<Any>> {
        val session = ParameterRenderSession(ParameterPlaceholderMode.Named, false)
        val sessionRegistry = RenderSessionRegistry(_registry, session)
        val sql = sessionRegistry.render(ast, this, null)
        return sql to session.occurrences.toList()
    }

    /**
     * Renders an identity-aware query AST twice using one immutable renderer-registry snapshot.
     * Callers must classify and validate the AST with query:meta_data before invoking this method.
     */
    protected fun renderIdentityAwarePasses(ast: IQueryAst): RenderedQuery {
        val rendererSnapshot = SnapshotRendererRegistry(_registry.renderers)
        val displaySession = ParameterRenderSession(ParameterPlaceholderMode.Named, true)
        val jdbcSession = ParameterRenderSession(ParameterPlaceholderMode.Positional, true)

        val displaySql = RenderSessionRegistry(rendererSnapshot, displaySession).render(ast, this, null)
        val jdbcSql = RenderSessionRegistry(rendererSnapshot, jdbcSession).render(ast, this, null)

        val displayOccurrences = typedOccurrences(displaySession, "display")
        val jdbcOccurrences = typedOccurrences(jdbcSession, "JDBC")
        check(displaySession.emittedParameterCount == jdbcSession.emittedParameterCount) {
            "Named and positional rendering emitted different parameter counts"
        }
        check(displayOccurrences.size == jdbcOccurrences.size) {
            "Named and positional rendering recorded different occurrence counts"
        }
        check(displayOccurrences.indices.all { displayOccurrences[it] === jdbcOccurrences[it] }) {
            "Named and positional rendering emitted different parameter occurrence sequences"
        }

        return RenderedQuery(
            displaySql = requireNotNull(displaySql) { "Identity-aware query rendered no display SQL" },
            jdbcSql = requireNotNull(jdbcSql) { "Identity-aware query rendered no positional SQL" },
            parameterOccurrences = jdbcOccurrences,
        )
    }

    private fun typedOccurrences(session: ParameterRenderSession, passName: String): List<SqlParameter<*>> {
        check(session.emittedParameterCount == session.occurrences.size) {
            "$passName renderer emission count ${session.emittedParameterCount} does not match " +
                "recorded occurrence count ${session.occurrences.size}"
        }
        return session.occurrences.mapIndexed { index, occurrence ->
            occurrence as? SqlParameter<*>
                ?: error("$passName occurrence at index $index is not a SqlParameter")
        }
    }

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
            throw UnsupportedOperationException("Renderer registry is immutable during identity-aware rendering")
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any, D : QueryDataClass?> get(type: KClass<A>): IAstRenderer<A, D>? =
            renderers[type] as? IAstRenderer<A, D>

        override fun render(ast: Any?, dialect: ISqlDialect, dataClass: QueryDataClass?): String? =
            error("Identity-aware rendering must dispatch through RenderSessionRegistry")
    }


}
