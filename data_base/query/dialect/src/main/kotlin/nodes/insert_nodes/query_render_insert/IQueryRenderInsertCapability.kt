package gog.my_project.data_base.query.dialect.nodes.insert_nodes.query_render_insert


import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.dialect.data_class.insert_data.query_render_insert.QueryRenderInsertData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryRenderInsertCapability: IAstRenderer<IQueryRenderInsertAst, QueryRenderInsertData>  {
}