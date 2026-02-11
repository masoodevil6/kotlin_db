package gog.my_project.data_base.query.dialect.nodes.condition_group

import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.dialect.data_class.conditions_group.QueryConditionsGroupsData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryConditionGroupCapability : IAstRenderer<IQueryConditionsGroupsAst, QueryConditionsGroupsData> {
}