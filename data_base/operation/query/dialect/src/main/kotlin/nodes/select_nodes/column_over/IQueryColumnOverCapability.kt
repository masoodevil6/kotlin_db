package gog.my_project.data_base_query.dialect.nodes.select_nodes.column_over

import gog.my_project.data_base_query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base_query.dialect.data_class.select_data.column_over.QueryColumnOverData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryColumnOverCapability  : IAstRenderer<IQueryColumnOverAst, QueryColumnOverData>  {

}