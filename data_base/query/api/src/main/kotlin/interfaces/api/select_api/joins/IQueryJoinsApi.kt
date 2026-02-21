package gog.my_project.data_base.query.api.interfaces.api.select_api.joins

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst

interface IQueryJoinsApi : IQueryApi<IQueryJoinsAst> {

    fun addJoin(blockJoin: IQueryJoinsItemApi.()-> Unit): IQueryJoinsApi;

}