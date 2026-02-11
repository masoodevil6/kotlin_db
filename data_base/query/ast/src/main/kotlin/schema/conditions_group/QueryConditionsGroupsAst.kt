package gog.my_project.data_base.query.ast.schema.conditions_group

import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.conditions.IQueryIsConditionAst

class QueryConditionsGroupsAst : IQueryConditionsGroupsAst {

    override var isAddLogical:     Boolean                                  = false ;
    override var conditionLogical: String?                                  = null;
    override var conditions:       MutableList<IQueryIsConditionAst>        = mutableListOf();

}