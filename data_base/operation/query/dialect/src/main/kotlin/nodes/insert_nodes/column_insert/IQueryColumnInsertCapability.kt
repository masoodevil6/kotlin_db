package gog.my_project.data_base_query.dialect.nodes.insert_nodes.column_insert

import gog.my_project.data_base_query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base_query.dialect.data_class.insert_data.column_insert.QueryColumnInsertData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryColumnInsertCapability : IAstRenderer<IQueryColumnInsertAst, QueryColumnInsertData>{

}