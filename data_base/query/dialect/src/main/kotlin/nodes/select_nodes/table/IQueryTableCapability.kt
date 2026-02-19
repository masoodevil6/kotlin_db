package gog.my_project.data_base.query.dialect.nodes.select_nodes.table

import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.dialect.data_class.select_data.table.QueryTableData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryTableCapability : IAstRenderer<IQueryTableAst, QueryTableData> {

}