package gog.my_project.data_base.query.api.interfaces.where

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst

interface IQueryWhereApi : IQueryApi {

    var ast: IQueryWhereAst;

    fun conditions(blockCondition: IQueryConditionsGroupsApi.() -> Unit): IQueryWhereApi;

}