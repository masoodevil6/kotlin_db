package gog.my_project.data_base.query.dialect.nodes.update_nodes.query_render_update

import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.data_class.update_data.query_render_update.QueryRenderUpdateData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryRenderUpdateCapability : IAstRenderer<IQueryRenderUpdateAst, QueryRenderUpdateData> {
}