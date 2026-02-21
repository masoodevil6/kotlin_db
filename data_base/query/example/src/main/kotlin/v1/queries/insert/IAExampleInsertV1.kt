package gog.my_project.data_base.query.example.v1.queries.insert

import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

interface IAExampleInsertV1 {

    fun query(): IQueryRenderInsertApi;

    fun execute(queryManager : IQueryBuilderExecutor);

}