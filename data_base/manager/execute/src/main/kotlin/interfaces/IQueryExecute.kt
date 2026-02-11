package gog.my_project.data_base.manager.execute.interfaces

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

interface IQueryExecute {

    fun execute(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult) -> Unit
    );

}