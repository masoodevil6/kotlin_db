package gog.my_project.data_base_query.dialect.nodes.select_nodes.table

import gog.my_project.data_base_query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base_query.dialect.data_class.select_data.table.QueryTableData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryTableCapability : IAstRenderer<IQueryTableAst, QueryTableData> {

}