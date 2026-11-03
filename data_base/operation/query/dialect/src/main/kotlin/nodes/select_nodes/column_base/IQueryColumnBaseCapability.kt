package gog.my_project.data_base_query.dialect.nodes.select_nodes.column_base

import gog.my_project.data_base_query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base_query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryColumnBaseCapability : IAstRenderer<IQueryColumnsBaseAst, QueryColumnBaseData> {

}