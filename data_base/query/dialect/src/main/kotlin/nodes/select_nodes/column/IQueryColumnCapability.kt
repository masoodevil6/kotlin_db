package gog.my_project.data_base.query.dialect.nodes.select_nodes.column

import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column.QueryColumnData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnCapability  : IAstRenderer<IQueryColumnsAst, QueryColumnData> {

}