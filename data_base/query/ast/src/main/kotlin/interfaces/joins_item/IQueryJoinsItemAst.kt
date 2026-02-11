package gog.my_project.data_base.query.ast.interfaces.joins_item

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst

interface IQueryJoinsItemAst  : IQueryAst {

    var joinType:         String?                   ;
    var joinTable:        IQueryTableAst;
    var joinConditions:   IQueryConditionsGroupsAst;

}