package gog.my_project.data_base_session.dialect.interfaces

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.dialect.data_class.RenderedSessionCommand
import gog.my_project.data_base.dialect.DatabaseDialect

interface ISessionDialect {
    val dialect: DatabaseDialect
    val registry: ISessionRendererRegistry

    fun render(ast: ISessionCommandAst): RenderedSessionCommand
}
