package gog.my_project.data_base_query.dialect.nodes.select_nodes.column_method.count

import gog.my_project.data_base_query.ast.interfaces.select_interface.column_method.count.IQueryColumnMethodCountAst
import gog.my_project.data_base_query.dialect.data_class.select_data.column_method.count.IQueryColumnMethodCountData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryColumnMethodCountCapability : IAstRenderer<IQueryColumnMethodCountAst, IQueryColumnMethodCountData> {

}
