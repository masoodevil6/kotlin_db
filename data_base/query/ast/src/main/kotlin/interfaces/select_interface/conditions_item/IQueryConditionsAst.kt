package gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item
import gog.my_project.data_base.query.ast.metadata.select.conditions_item.QueryConditionsItemAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.conditions.IQueryIsConditionAst
import gog.my_project.data_base.query.ast.metadata.select.conditions_group.QueryConditionsGroupsAstMetadata

interface IQueryConditionsAst : IQueryIsConditionAst<QueryConditionsItemAstMetadata> {

    var conditionSideLeft:   IQueryColumnsBaseAst?
    var conditionSideRight : Any?

}
