package gog.my_project.data_base_schema.dialect.manager

import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.interfaces.IRendererRegistry
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect

class RenderContext(
    override val dialect: ISqlDialect,
    override val registry: IRendererRegistry
) : IRenderContext
{

}