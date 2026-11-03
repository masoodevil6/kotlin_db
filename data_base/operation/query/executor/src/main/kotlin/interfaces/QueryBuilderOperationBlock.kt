package gog.my_project.data_base_query.executor.interfaces

import gog.my_project.data_base_query.executor.result.QueryBuilderSelection

/** Selects one typed query operation inside the unified queryBuilder callback. */
fun interface QueryBuilderOperationBlock<T> {
    fun build(query: QueryBuilderSelection): T
}
