package gog.my_project.data_base.query.dialect.nodes.option_order

import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.dialect.data_class.option_order.QueryOptionOrderData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryOptionOrderCapability : IAstRenderer<IQueryOptionOrderAst , QueryOptionOrderData> {
}