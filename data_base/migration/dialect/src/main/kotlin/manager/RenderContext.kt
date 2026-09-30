package gog.my_project.data_base.migration.dialect.manager

import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.interfaces.IRendererRegistry
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect

class RenderContext(
    override val dialect: ISqlDialect,
    override val registry: IRendererRegistry
) : IRenderContext
{

}