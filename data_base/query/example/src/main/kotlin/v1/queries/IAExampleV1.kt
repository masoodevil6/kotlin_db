package  gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.query.api.interfaces.api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor


interface IAExampleV1 {

    fun query(): IQueryRenderSelectApi;

    fun execute(queryManager : IQueryBuilderExecutor);

}