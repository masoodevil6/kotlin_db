package gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.conditions.IQueryIsConditionAst

interface IQueryConditionsGroupsAst : IQueryIsConditionAst, IQueryAst {

    var conditionLogical: String?                          ;
    var conditions:       MutableList<IQueryIsConditionAst>;

}