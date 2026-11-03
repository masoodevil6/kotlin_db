package gog.my_project.data_base_query.dialect.nodes.select_nodes.select

import gog.my_project.data_base_query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base_query.dialect.data_class.select_data.select.QuerySelectData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQuerySelectCapability  : IAstRenderer<IQuerySelectAst, QuerySelectData> {

}