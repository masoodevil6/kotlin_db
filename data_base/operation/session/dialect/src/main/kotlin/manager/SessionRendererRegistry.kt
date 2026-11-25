package gog.my_project.data_base_session.dialect.manager

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.dialect.interfaces.ISessionAstRenderer
import gog.my_project.data_base_session.dialect.interfaces.ISessionDialect
import gog.my_project.data_base_session.dialect.interfaces.ISessionRendererRegistry
import kotlin.reflect.KClass

class SessionRendererRegistry : ISessionRendererRegistry {
    private val renderers = linkedMapOf<
        KClass<out ISessionCommandAst>,
        ISessionAstRenderer<out ISessionCommandAst>,
    >()

    override fun <A : ISessionCommandAst> register(
        astType: KClass<A>,
        renderer: ISessionAstRenderer<A>,
    ) {
        require(astType !in renderers) {
            "A Session renderer is already registered for ${astType.qualifiedName}."
        }
        renderers[astType] = renderer
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : ISessionCommandAst> get(astType: KClass<A>): ISessionAstRenderer<A>? =
        renderers[astType] as? ISessionAstRenderer<A>

    override fun resolve(ast: ISessionCommandAst): ISessionAstRenderer<out ISessionCommandAst> =
        findEntry(ast).renderer

    @Suppress("UNCHECKED_CAST")
    override fun render(
        ast: ISessionCommandAst,
        dialect: ISessionDialect,
        state: SessionRenderState,
    ): String? {
        validate(ast)
        val entry = findEntry(ast)
        val checkpoint = state.checkpoint()
        val renderer = entry.renderer as ISessionAstRenderer<ISessionCommandAst>
        val context = SessionRenderContext(dialect, this, state)

        return try {
            renderer.render(ast, context).also { sql ->
                if (sql == null) state.rollbackTo(checkpoint)
            }
        } catch (failure: Throwable) {
            rollbackAfterFailure(state, checkpoint, failure)
            when (failure) {
                is SessionRenderException -> throw failure
                is Exception -> throw SessionRenderException.RendererFailed(ast::class, failure)
                else -> throw failure
            }
        }
    }

    private fun findEntry(ast: ISessionCommandAst): RendererEntry {
        val astClass = ast::class
        val matches = renderers.entries.filter { (registeredType, _) ->
            registeredType.java.isAssignableFrom(astClass.java)
        }

        if (matches.isEmpty()) {
            throw SessionRenderException.RendererNotFound(astClass)
        }

        val mostSpecificMatches = matches.filter { (candidateType, _) ->
            matches.none { (otherType, _) ->
                candidateType != otherType && candidateType.java.isAssignableFrom(otherType.java)
            }
        }

        if (mostSpecificMatches.size > 1) {
            throw SessionRenderException.AmbiguousRenderer(
                astClass,
                mostSpecificMatches.map { it.key },
            )
        }

        return RendererEntry(mostSpecificMatches.single().value)
    }

    private fun validate(ast: ISessionCommandAst) {
        when (ast) {
            is IAcquireSessionAdvisoryLockAst -> {
                if (ast.metadata.resource == null) {
                    throw SessionRenderException.InvalidAst("Acquire lock resource is missing.")
                }
                if (ast.metadata.waitPolicy == null) {
                    throw SessionRenderException.InvalidAst("Acquire lock wait policy is missing.")
                }
            }

            is IReleaseSessionAdvisoryLockAst -> {
                if (ast.metadata.resource == null) {
                    throw SessionRenderException.InvalidAst("Release lock resource is missing.")
                }
            }

            else -> throw SessionRenderException.InvalidAst(
                "Unsupported command AST type ${ast::class.qualifiedName}.",
            )
        }
    }

    private fun rollbackAfterFailure(
        state: SessionRenderState,
        checkpoint: SessionRenderState.Checkpoint,
        failure: Throwable,
    ) {
        try {
            state.rollbackTo(checkpoint)
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressed(rollbackFailure)
        }
    }

    private data class RendererEntry(
        val renderer: ISessionAstRenderer<out ISessionCommandAst>,
    )
}
