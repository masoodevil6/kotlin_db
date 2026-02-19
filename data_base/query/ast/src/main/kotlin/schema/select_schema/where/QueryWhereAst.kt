package gog.my_project.data_base.query.ast.schema.select_schema_ast.where

import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst

class QueryWhereAst : IQueryWhereAst {

    override var condition: IQueryConditionsGroupsAst?  = null;

}