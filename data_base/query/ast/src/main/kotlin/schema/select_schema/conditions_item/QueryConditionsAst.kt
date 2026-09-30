package gog.my_project.data_base.query.ast.schema.select_schema.conditions_item

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst

class QueryConditionsAst : IQueryConditionsAst {

    override var isAddLogical:        Boolean                  = false ;
    override var conditionLogical:    String?                  = null;
    override var conditionSideLeft:   IQueryColumnsBaseAst?    = null;
    override var conditionOperation:  String?                  = null;
    override var conditionSideRight : Any?                     = null;

}