package gog.my_project.data_base.query.ast.schema.select_schema.conditions_item
import gog.my_project.data_base.query.ast.metadata.select.conditions_item.QueryConditionsItemAstMetadata

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst

class QueryConditionsAst(
    override var metadata: QueryConditionsItemAstMetadata = QueryConditionsItemAstMetadata()
) : IQueryConditionsAst {

    override var conditionSideLeft:   IQueryColumnsBaseAst?    = null;

    override var conditionSideRight : Any?                     = null;

}
