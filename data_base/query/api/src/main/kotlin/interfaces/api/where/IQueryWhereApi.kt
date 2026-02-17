package gog.my_project.data_base.query.api.interfaces.api.where

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst

interface IQueryWhereApi : IQueryApi {

    var ast: IQueryWhereAst;

    fun conditions(blockCondition: IQueryConditionsGroupsApi.() -> Unit): IQueryWhereApi;

}