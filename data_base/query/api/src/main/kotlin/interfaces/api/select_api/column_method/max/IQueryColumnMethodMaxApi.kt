package gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.max

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst

interface IQueryColumnMethodMaxApi : IQueryApi<IQueryColumnMethodMaxAst> {

    fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodMaxApi

    fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodMaxApi

}