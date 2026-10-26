package gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.min

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.min.QueryColumnMethodMinData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnMethodMinCapability : IAstRenderer<IQueryColumnMethodMinAst, QueryColumnMethodMinData>  {
}