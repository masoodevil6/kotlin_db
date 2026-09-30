package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry

class RenderContext(
    override val dialect: ISqlDialect,
    override val registry: IRendererRegistry
) : IRenderContext
{

}