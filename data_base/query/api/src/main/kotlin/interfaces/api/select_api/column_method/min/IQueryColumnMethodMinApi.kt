package gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.min

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst

interface IQueryColumnMethodMinApi : IQueryApi<IQueryColumnMethodMinAst> {

    fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodMinApi

    fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodMinApi

}