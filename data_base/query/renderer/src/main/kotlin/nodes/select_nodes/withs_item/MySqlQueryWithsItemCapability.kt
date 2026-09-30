package gog.my_project.data_base.query.renderer.nodes.select_nodes.withs_item

import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base.query.dialect.data_class.select_data.withs_item.QueryWithsItemData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.withs_item.IQueryWithsItemCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryWithsItemCapability:
    IQueryWithsItemCapability {

    override fun render(
        ast:        IQueryWithsItemAst,
        ctx:        IRenderContext,
        dataClass:  QueryWithsItemData?
    ): String? {

        val withName : String? = ast.withName;
        val withBody:  IQueryRenderSelectAst? = ast.withBody;
        val withBodyStr =
            ctx.registry.render(
                withBody ,
                ctx.dialect ,
                IQueryRenderSelectData()
            );

        if (withName != null && withBodyStr != null) {
            return " $withName AS  ($withBodyStr) " ;
        }
        return null;
    }

}