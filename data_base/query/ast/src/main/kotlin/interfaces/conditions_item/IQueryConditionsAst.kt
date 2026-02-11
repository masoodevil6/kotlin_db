package gog.my_project.data_base.query.ast.interfaces.condition_item

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.conditions.IQueryIsConditionAst

interface IQueryConditionsAst : IQueryIsConditionAst , IQueryAst {

    var conditionLogical:    String?                  ;
    var conditionSideLeft:   IQueryColumnsBaseAst?    ;
    var conditionOperation:  String           ?       ;
    var conditionSideRight : Any?                     ;

}