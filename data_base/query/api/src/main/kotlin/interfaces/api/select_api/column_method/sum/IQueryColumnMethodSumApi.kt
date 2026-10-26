package gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.sum

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst

interface IQueryColumnMethodSumApi : IQueryApi<IQueryColumnMethodSumAst> {

    fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodSumApi

    fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodSumApi

}
