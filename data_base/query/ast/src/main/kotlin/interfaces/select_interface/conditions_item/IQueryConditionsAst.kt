package gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.conditions.IQueryIsConditionAst

interface IQueryConditionsAst : IQueryIsConditionAst, IQueryAst {

    var conditionLogical:    String?                  ;
    var conditionSideLeft:   IQueryColumnsBaseAst?    ;
    var conditionOperation:  String           ?       ;
    var conditionSideRight : Any?                     ;

}