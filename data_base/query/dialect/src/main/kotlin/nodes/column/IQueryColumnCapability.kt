package gog.my_project.data_base.query.dialect.nodes.column

import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst
import gog.my_project.data_base.query.dialect.data_class.column.QueryColumnData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnCapability  : IAstRenderer<IQueryColumnsAst, QueryColumnData> {

}