package gog.my_project.data_base.query.ast.interfaces.select_interface.where

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst

interface IQueryWhereAst : IQueryAst {

    var condition: IQueryConditionsGroupsAst?;

}