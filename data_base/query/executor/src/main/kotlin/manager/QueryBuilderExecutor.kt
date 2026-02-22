package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import java.sql.ResultSet

class QueryBuilderExecutor : IQueryBuilderExecutor {

    override fun execute(
        queryBuilder:   IQueryRenderSelectApi,
        blockExecute:   (ExecuteResult<ResultSet>) -> Unit,
        blockQueryInfo: ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)?
    ){
        return executeOut(
            queryBuilder = queryBuilder,
            blockQueryInfo =  blockQueryInfo,
            blockResult=   {
                db: IQueryExecute, builtQuery: BuiltQuery->
                db.executeSelect(
                    builtQuery = builtQuery,
                    blockExecute = blockExecute
                )
            }
        );
    }

    override fun execute(
        queryBuilder: IQueryRenderInsertApi,
        blockExecute: (ExecuteResult<Long>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        return executeOut(
            queryBuilder = queryBuilder,
            blockQueryInfo =  blockQueryInfo,
            blockResult=   {
                db: IQueryExecute, builtQuery: BuiltQuery->
                db.executeInsert(
                    builtQuery = builtQuery,
                    blockExecute = blockExecute
                )
            }
        );

    }

}