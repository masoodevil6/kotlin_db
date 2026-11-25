package gog.my_project.data_base_session.dialect.interfaces

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.dialect.manager.SessionRenderState
import kotlin.reflect.KClass

interface ISessionRendererRegistry {
    fun <A : ISessionCommandAst> register(
        astType: KClass<A>,
        renderer: ISessionAstRenderer<A>,
    )

    fun <A : ISessionCommandAst> get(astType: KClass<A>): ISessionAstRenderer<A>?

    fun resolve(ast: ISessionCommandAst): ISessionAstRenderer<out ISessionCommandAst>

    fun render(
        ast: ISessionCommandAst,
        dialect: ISessionDialect,
        state: SessionRenderState,
    ): String?
}
