package gog.my_project.data_base.query.api.interfaces.option_group

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst

interface IQueryOptionGroupApi : IQueryApi {

    var ast: IQueryOptionGroupAst;

    fun addColumn(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryOptionGroupApi

}