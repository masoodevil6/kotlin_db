package gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.avg

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst

interface IQueryColumnMethodAvgApi : IQueryApi<IQueryColumnMethodAvgAst> {

    fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodAvgApi
    fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodAvgApi

}
