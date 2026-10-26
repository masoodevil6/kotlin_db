package gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.max

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.max.QueryColumnMethodMaxData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnMethodMaxCapability : IAstRenderer<IQueryColumnMethodMaxAst, QueryColumnMethodMaxData>  {
}