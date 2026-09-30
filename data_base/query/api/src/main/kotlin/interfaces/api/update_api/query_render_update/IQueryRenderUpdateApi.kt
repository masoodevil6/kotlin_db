package gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.column_update.IQueryColumnUpdateApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst

interface IQueryRenderUpdateApi : IQueryApi<IQueryRenderUpdateAst>{

    fun addValue(blockColumn: IQueryColumnUpdateApi.() -> Unit): IQueryRenderUpdateApi;

    fun table(blockTable: IQueryTableApi.() -> Unit): IQueryRenderUpdateApi;
    fun where(blockGroup: IQueryWhereApi.() -> Unit): IQueryRenderUpdateApi;

}