package gog.my_project.data_base_query.dialect.nodes.select_nodes.query

import gog.my_project.data_base_query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base_query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryRenderSelectCapability : IAstRenderer<IQueryRenderSelectAst, IQueryRenderSelectData> {

}