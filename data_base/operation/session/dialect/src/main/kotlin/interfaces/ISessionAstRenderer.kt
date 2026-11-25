package gog.my_project.data_base_session.dialect.interfaces

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst

interface ISessionAstRenderer<A : ISessionCommandAst> {
    fun render(
        ast: A,
        context: ISessionRenderContext,
    ): String?
}
