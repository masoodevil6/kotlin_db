package gog.my_project.data_base_session.dialect.manager

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.dialect.data_class.RenderedSessionCommand
import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.dialect.interfaces.ISessionDialect
import gog.my_project.data_base_session.dialect.interfaces.ISessionRendererRegistry

abstract class BaseSessionDialect(
    final override val dialect: SessionDialect,
    registerRenders: ISessionRendererRegistry.() -> Unit = {},
) : ISessionDialect {

    final override val registry: ISessionRendererRegistry = SessionRendererRegistry()

    init {
        registry.registerRenders()
    }

    final override fun render(ast: ISessionCommandAst): RenderedSessionCommand {
        val state = SessionRenderState()
        val sql = registry.render(ast, this, state)
            ?: throw SessionRenderException.RendererReturnedNull(ast::class)

        return RenderedSessionCommand(
            kind = ast.kind,
            sql = sql,
            parameters = state.snapshot(),
        )
    }
}
