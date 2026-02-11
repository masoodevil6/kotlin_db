package gog.my_project.data_base.query.ast.schema.conditions_item

import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.condition_item.IQueryConditionsAst

class QueryConditionsAst : IQueryConditionsAst {

    override var isAddLogical:        Boolean                  = false ;
    override var conditionLogical:    String?                  = null;
    override var conditionSideLeft:   IQueryColumnsBaseAst?    = null;
    override var conditionOperation:  String?                  = null;
    override var conditionSideRight : Any?                     = null;

}