package gog.my_project.data_base.query.dialect.nodes.update_nodes.column_update

import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.dialect.data_class.update_data.column_update.QueryColumnUpdateData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnUpdateCapability : IAstRenderer<IQueryColumnUpdateAst, QueryColumnUpdateData> {
}