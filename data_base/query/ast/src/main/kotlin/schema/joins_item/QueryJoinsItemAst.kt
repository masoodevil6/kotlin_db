package gog.my_project.data_base.query.ast.schema.joins_item

import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.ast.schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.table.QueryTableAst

class QueryJoinsItemAst : IQueryJoinsItemAst {

    override var joinType:         String?                   = null;
    override var joinTable:        IQueryTableAst            = QueryTableAst();
    override var joinConditions:   IQueryConditionsGroupsAst = QueryConditionsGroupsAst()

}