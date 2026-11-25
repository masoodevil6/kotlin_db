package gog.my_project.data_base_session.dialect.manager

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.dialect.interfaces.ISessionDialect
import gog.my_project.data_base_session.dialect.interfaces.ISessionRenderContext
import gog.my_project.data_base_session.dialect.interfaces.ISessionRendererRegistry

class SessionRenderContext internal constructor(
    override val dialect: ISessionDialect,
    override val registry: ISessionRendererRegistry,
    private val state: SessionRenderState,
) : ISessionRenderContext {

    override fun emitParameter(parameter: SessionCommandParameter): String {
        state.emit(parameter)
        return "?"
    }

    override fun checkpoint(): SessionRenderState.Checkpoint = state.checkpoint()

    override fun rollbackTo(checkpoint: SessionRenderState.Checkpoint) {
        state.rollbackTo(checkpoint)
    }

    override fun render(ast: ISessionCommandAst): String =
        registry.render(ast, dialect, state)
            ?: throw SessionRenderException.RendererReturnedNull(ast::class)
}
