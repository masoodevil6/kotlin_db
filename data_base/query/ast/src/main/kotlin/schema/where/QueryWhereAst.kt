package gog.my_project.data_base.query.ast.schema.where

import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst

class QueryWhereAst : IQueryWhereAst {

    override var condition: IQueryConditionsGroupsAst?  = null;

}