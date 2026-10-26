package gog.my_project.data_base.query.example.v3

import gog.my_project.collection.ICollection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

/** Compile-time consumer fixture for the executor's transitive public Collection API. */
internal fun typedQueryConsumerApiCompileFixture(
    queryManager: IQueryBuilderExecutor,
    query: IQueryRenderSelectApi,
): ExecuteResult<ICollection<Int>> = queryManager.queryBuilder(query).executeAs { row ->
    row.getValue("id") as Int
}
