package gog.my_project.data_base_query.dialect.nodes.select_nodes.column

import gog.my_project.data_base_query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base_query.dialect.data_class.select_data.column.QueryColumnData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryColumnCapability  : IAstRenderer<IQueryColumnsAst, QueryColumnData> {

}