package gog.my_project.data_base.query.dialect.nodes.query

import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.dialect.data_class.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryRenderSelectCapability : IAstRenderer<IQueryRenderSelectAst , IQueryRenderSelectData> {

}