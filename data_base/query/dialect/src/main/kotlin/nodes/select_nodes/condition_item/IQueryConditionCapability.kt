package gog.my_project.data_base.query.dialect.nodes.select_nodes.condition_item

import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item.QueryConditionsData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryConditionCapability: IAstRenderer<IQueryConditionsAst, QueryConditionsData> {
}