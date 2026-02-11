package gog.my_project.data_base.query.dialect.nodes.select

import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst
import gog.my_project.data_base.query.dialect.data_class.select.QuerySelectData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQuerySelectCapability  : IAstRenderer<IQuerySelectAst , QuerySelectData> {

}