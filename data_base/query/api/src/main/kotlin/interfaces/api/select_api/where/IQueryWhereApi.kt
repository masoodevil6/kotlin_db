package gog.my_project.data_base.query.api.interfaces.api.select_api.where

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst

interface IQueryWhereApi : IQueryApi<IQueryWhereAst> {

    fun conditions(blockCondition: IQueryConditionsGroupsApi.() -> Unit): IQueryWhereApi;

}