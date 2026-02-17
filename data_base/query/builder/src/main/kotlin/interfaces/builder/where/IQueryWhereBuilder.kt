package gog.my_project.data_base.query.builder.interfaces.builder.where

import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import gog.my_project.data_base.query.builder.interfaces.builder.conditions_group.IQueryConditionsGroupsBuilder

interface IQueryWhereBuilder : IQuery {

    var ast: IQueryWhereAst;

    fun conditions(blockCondition: IQueryConditionsGroupsBuilder.() -> Unit): IQueryWhereBuilder;

}