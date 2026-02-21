package gog.my_project.data_base.query.dialect.manager

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
//import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData
//import gog.my_project.data_base.query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import gog.my_project.data_base.query.renderer.manager.RendererRegistry

abstract class BaseSqlDialect(
) : ISqlDialect{

    override val _registry: IRendererRegistry = RendererRegistry()

    init {
        registerRenders()
    }


    override fun <Ast : IQueryAst> render(
        ast: Ast
    ): String? {
        return _registry.render(ast , this , null);
    }


}