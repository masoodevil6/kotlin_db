package gog.my_project.data_base.query.dialect.nodes.select_nodes.condition_group

import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_group.QueryConditionsGroupsData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryConditionGroupCapability : IAstRenderer<IQueryConditionsGroupsAst, QueryConditionsGroupsData> {
}