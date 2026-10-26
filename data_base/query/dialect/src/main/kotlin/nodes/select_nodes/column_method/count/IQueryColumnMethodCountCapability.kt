package gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.count

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.count.IQueryColumnMethodCountAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.count.IQueryColumnMethodCountData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnMethodCountCapability : IAstRenderer<IQueryColumnMethodCountAst, IQueryColumnMethodCountData> {

}
