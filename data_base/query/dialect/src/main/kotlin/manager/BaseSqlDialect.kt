package gog.my_project.data_base.query.dialect.manager

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import gog.my_project.data_base.query.renderer.manager.RenderSessionRegistry
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

    /** Renders once while collecting Builder parameter references in placeholder order. */
    fun renderWithBindings(ast: IQueryRenderSelectAst): Pair<String?, List<Any>> {
        val occurrences = mutableListOf<Any>()
        val sessionRegistry = RenderSessionRegistry(_registry, occurrences)
        val sql = sessionRegistry.render(ast, this, null)
        return sql to occurrences.toList()
    }


}
