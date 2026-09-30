package gog.my_project.data_base.query.dialect.nodes.select_nodes.select

import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.dialect.data_class.select_data.select.QuerySelectData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQuerySelectCapability  : IAstRenderer<IQuerySelectAst, QuerySelectData> {

}