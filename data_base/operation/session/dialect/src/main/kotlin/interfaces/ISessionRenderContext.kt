package gog.my_project.data_base_session.dialect.interfaces

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base_session.dialect.manager.SessionRenderState

interface ISessionRenderContext {
    val dialect: ISessionDialect
    val registry: ISessionRendererRegistry

    /** Records a parameter occurrence and returns its positional SQL placeholder. */
    fun emitParameter(parameter: SessionCommandParameter): String

    fun checkpoint(): SessionRenderState.Checkpoint

    fun rollbackTo(checkpoint: SessionRenderState.Checkpoint)

    /** Dispatches a nested command with a fresh context and this command's shared state. */
    fun render(ast: ISessionCommandAst): String
}
