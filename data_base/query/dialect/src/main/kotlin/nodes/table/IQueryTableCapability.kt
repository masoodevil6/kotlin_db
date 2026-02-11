package gog.my_project.data_base.query.dialect.nodes.table

import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.dialect.data_class.table.QueryTableData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryTableCapability : IAstRenderer<IQueryTableAst , QueryTableData> {

}