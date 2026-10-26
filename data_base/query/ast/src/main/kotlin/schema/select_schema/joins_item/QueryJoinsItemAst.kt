package gog.my_project.data_base.query.ast.schema.select_schema.joins_item
import gog.my_project.data_base.query.ast.metadata.select.joins_item.QueryJoinsItemAstMetadata

import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst

class QueryJoinsItemAst(
    override var metadata: QueryJoinsItemAstMetadata = QueryJoinsItemAstMetadata()
) : IQueryJoinsItemAst {

    override var joinTable:        IQueryTableAst            =   QueryTableAst()
    override var joinConditions:   IQueryConditionsGroupsAst =   QueryConditionsGroupsAst()

}
