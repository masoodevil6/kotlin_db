package gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst

interface IQueryRenderInsertApi : IQueryApi<IQueryRenderInsertAst> {

    fun table(blockTable: IQueryTableApi.() -> Unit): IQueryRenderInsertApi;

    fun addValue(blockColumn: IQueryColumnInsertApi.() -> Unit): IQueryRenderInsertApi;

}