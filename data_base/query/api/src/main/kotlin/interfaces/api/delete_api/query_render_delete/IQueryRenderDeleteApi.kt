package gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst

interface IQueryRenderDeleteApi : IQueryApi<IQueryRenderDeleteAst> {

    fun addTarget(target: String): IQueryRenderDeleteApi;

    fun table(blockTable: IQueryTableApi.() -> Unit): IQueryRenderDeleteApi;
    fun where(blockGroup: IQueryWhereApi.() -> Unit): IQueryRenderDeleteApi;

}