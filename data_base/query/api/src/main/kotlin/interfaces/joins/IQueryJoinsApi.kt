package gog.my_project.data_base.query.api.interfaces.joins

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst

interface IQueryJoinsApi : IQueryApi {

    var ast: IQueryJoinsAst;

    fun addJoin(blockJoin: IQueryJoinsItemApi.()-> Unit): IQueryJoinsApi;

}