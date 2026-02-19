package gog.my_project.data_base.query.example.v1.queries.select

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor


interface IAExampleSelectV1 {

    fun query(): IQueryRenderSelectApi;

    fun execute(queryManager : IQueryBuilderExecutor);

}