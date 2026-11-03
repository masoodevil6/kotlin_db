package gog.my_project.data_base_query.dialect.nodes.update_nodes.column_update

import gog.my_project.data_base_query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base_query.dialect.data_class.update_data.column_update.QueryColumnUpdateData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryColumnUpdateCapability : IAstRenderer<IQueryColumnUpdateAst, QueryColumnUpdateData> {
}