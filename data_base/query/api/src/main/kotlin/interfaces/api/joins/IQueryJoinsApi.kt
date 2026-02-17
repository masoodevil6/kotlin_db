package gog.my_project.data_base.query.api.interfaces.api.joins

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst

interface IQueryJoinsApi : IQueryApi {

    var ast: IQueryJoinsAst;

    fun addJoin(blockJoin: IQueryJoinsItemApi.()-> Unit): IQueryJoinsApi;

}