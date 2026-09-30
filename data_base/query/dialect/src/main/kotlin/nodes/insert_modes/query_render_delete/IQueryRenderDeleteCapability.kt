package gog.my_project.data_base.query.dialect.nodes.insert_modes.query_render_delete

import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.dialect.data_class.delete_data.query_render_delete.QueryRenderDeleteData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryRenderDeleteCapability : IAstRenderer<IQueryRenderDeleteAst, QueryRenderDeleteData> {
}