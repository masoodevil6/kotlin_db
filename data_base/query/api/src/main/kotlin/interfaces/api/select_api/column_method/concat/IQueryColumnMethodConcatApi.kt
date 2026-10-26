package gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.concat

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst

interface IQueryColumnMethodConcatApi : IQueryApi<IQueryColumnMethodConcatAst> {

    fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodConcatApi

    fun string(str : String) : IQueryColumnMethodConcatApi

    fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodConcatApi
}