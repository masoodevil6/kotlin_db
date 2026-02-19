package gog.my_project.data_base.query.dialect.manager

import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import gog.my_project.data_base.query.renderer.manager.RendererRegistry

abstract class BaseSqlDialect(
) : ISqlDialect {

    override val _registry: IRendererRegistry = RendererRegistry()

    init {
        registerRenders()
    }

    override fun render(ast: IQueryRenderSelectAst, dataClass: gog.my_project.data_base.query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData?) : String?{
        return _registry.render(ast , this , dataClass);
    }

}