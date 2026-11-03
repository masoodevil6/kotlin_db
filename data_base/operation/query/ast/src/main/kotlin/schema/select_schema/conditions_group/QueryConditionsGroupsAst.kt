package gog.my_project.data_base_query.ast.schema.select_schema.conditions_group
import gog.my_project.data_base_query.ast.metadata.select.conditions_group.QueryConditionsGroupsAstMetadata

import gog.my_project.data_base_query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.conditions.IQueryIsConditionAst

class QueryConditionsGroupsAst(
    override var metadata: QueryConditionsGroupsAstMetadata = QueryConditionsGroupsAstMetadata()
) : IQueryConditionsGroupsAst {

    override var conditions: MutableList<IQueryIsConditionAst<*>> = mutableListOf();

}
