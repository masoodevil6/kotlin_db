package gog.my_project.data_base_session.dialect.interfaces

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.dialect.data_class.RenderedSessionCommand
import gog.my_project.data_base_session.dialect.enums.SessionDialect

interface ISessionDialect {
    val dialect: SessionDialect
    val registry: ISessionRendererRegistry

    fun render(ast: ISessionCommandAst): RenderedSessionCommand
}
